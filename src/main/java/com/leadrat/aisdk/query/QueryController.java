package com.leadrat.aisdk.query;

import com.leadrat.aisdk.audit.AuditLogService;
import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.ReadOnlyViolationException;
import com.leadrat.aisdk.meeting.MeetingDiscussionProvider;
import com.leadrat.aisdk.security.JwtAuthFilter;
import com.leadrat.aisdk.whatsapp.WhatsappContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai-sdk")
public class QueryController {

    private final AiSdkProperties properties;
    private final SchemaCatalog catalog;
    private final QueryPlanner planner;
    private final QueryPlanValidator validator;
    private final TraversalEngine traversalEngine;
    private final Summarizer summarizer;
    private final RateLimiter rateLimiter;
    private final AuditLogService auditLog;
    private final MeetingDiscussionProvider discussionProvider;
    private final WhatsappContextProvider whatsappProvider;

    public QueryController(AiSdkProperties properties, SchemaCatalog catalog, QueryPlanner planner,
                           QueryPlanValidator validator, TraversalEngine traversalEngine, Summarizer summarizer,
                           RateLimiter rateLimiter, AuditLogService auditLog,
                           MeetingDiscussionProvider discussionProvider, WhatsappContextProvider whatsappProvider) {
        this.properties = properties;
        this.catalog = catalog;
        this.planner = planner;
        this.validator = validator;
        this.traversalEngine = traversalEngine;
        this.summarizer = summarizer;
        this.rateLimiter = rateLimiter;
        this.auditLog = auditLog;
        this.discussionProvider = discussionProvider;
        this.whatsappProvider = whatsappProvider;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request, HttpServletRequest httpRequest) {
        long started = System.currentTimeMillis();
        String subject = String.valueOf(httpRequest.getAttribute(JwtAuthFilter.SUBJECT_ATTRIBUTE));

        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question is required"));
        }
        if (request.targets() == null || request.targets().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "at least one target is required"));
        }
        if (request.targets().size() > properties.getQuery().getMaxTargetsPerRequest()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "too many targets, maximum is " + properties.getQuery().getMaxTargetsPerRequest()));
        }
        for (QueryRequest.Target target : request.targets()) {
            if (target.entity() == null || target.id() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "each target requires entity and id"));
            }
            if (!catalog.enabled(target.entity())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "entity is not enabled for querying: " + target.entity()));
            }
        }
        if (!rateLimiter.allow(subject)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "rate limit exceeded, retry in a minute"));
        }

        List<String> targetEntities = request.targets().stream()
                .map(QueryRequest.Target::entity).distinct().toList();

        QueryPlan plan = planner.plan(request.question(), catalog.describe(targetEntities), request.targets());
        EffectivePlan effectivePlan = validator.validate(plan, request, targetEntities);

        List<TraversalResult> results;
        try {
            results = traversalEngine.traverse(request.targets(), effectivePlan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            ReadOnlyViolationException violation = readOnlyViolation(e);
            if (violation == null) {
                throw e;
            }
            auditLog.record(request.question(), targetSummary(request), String.join(",", targetEntities), 0, false,
                    System.currentTimeMillis() - started, properties.getLlm().getPlannerModel());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", violation.getMessage()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        LinkedHashSet<String> entitiesTouched = new LinkedHashSet<>();
        int totalRows = 0;
        boolean hasDiscussions = false;
        boolean hasChats = false;
        for (int i = 0; i < results.size(); i++) {
            TraversalResult result = results.get(i);
            String targetPhone = request.targets().get(i).phone();
            if (targetPhone == null || targetPhone.isBlank()) {
                targetPhone = result.phone();
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("self", result.self());
            node.put("parents", result.parents());
            node.put("children", result.children());
            List<Map<String, Object>> discussions = discussionProvider.forTarget(result.entity(), result.id());
            if (!discussions.isEmpty()) {
                node.put("meetingDiscussions", discussions);
                totalRows += discussions.size();
                hasDiscussions = true;
            }
            List<Map<String, Object>> chats = whatsappProvider.forTarget(result.entity(), result.id(),
                    targetPhone, result.self());
            if (!chats.isEmpty()) {
                node.put("whatsappChats", chats);
                totalRows += chats.size();
                hasChats = true;
            }
            data.put(result.entity() + ":" + result.id(), node);
            entitiesTouched.add(result.entity());
            result.parents().forEach(parent -> entitiesTouched.add(parent.entity()));
            totalRows += 1 + result.parents().size();
            for (TraversalResult.ChildGroup group : result.children()) {
                entitiesTouched.add(group.entity());
                totalRows += group.items().size();
            }
        }

        String answer;
        try {
            answer = summarizer.summarize(request.question(), data,
                    catalog.instructionsFor(new ArrayList<>(entitiesTouched)), hasDiscussions, hasChats);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "summarizer call failed: " + e.getMessage()));
        }

        long latency = System.currentTimeMillis() - started;
        QueryResponse response = new QueryResponse(answer, request.targets(), data,
                new QueryResponse.Meta(false, Instant.now().toString(), properties.getLlm().getPlannerModel(),
                        properties.getLlm().getSummarizerModel(), effectivePlan.parentDepth(),
                        effectivePlan.childDepth(), latency));
        auditLog.record(request.question(), targetSummary(request), String.join(",", entitiesTouched),
                totalRows, false, latency, properties.getLlm().getSummarizerModel());
        return ResponseEntity.ok(response);
    }

    private ReadOnlyViolationException readOnlyViolation(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ReadOnlyViolationException violation) {
                return violation;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    private String targetSummary(QueryRequest request) {
        return request.targets().stream()
                .map(t -> t.entity() + ":" + t.id())
                .collect(Collectors.joining(","));
    }
}
