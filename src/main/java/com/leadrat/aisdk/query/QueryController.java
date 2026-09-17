package com.leadrat.aisdk.query;

import com.leadrat.aisdk.audit.AuditLogService;
import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.security.JwtAuthFilter;
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
    private final QueryCache cache;
    private final RateLimiter rateLimiter;
    private final AuditLogService auditLog;

    public QueryController(AiSdkProperties properties, SchemaCatalog catalog, QueryPlanner planner,
                           QueryPlanValidator validator, TraversalEngine traversalEngine, Summarizer summarizer,
                           QueryCache cache, RateLimiter rateLimiter, AuditLogService auditLog) {
        this.properties = properties;
        this.catalog = catalog;
        this.planner = planner;
        this.validator = validator;
        this.traversalEngine = traversalEngine;
        this.summarizer = summarizer;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.auditLog = auditLog;
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

        String cacheKey = cache.key(request, effectivePlan, catalog.configVersion());
        QueryResponse cached = cache.get(cacheKey);
        if (cached != null) {
            long latency = System.currentTimeMillis() - started;
            auditLog.record(request.question(), targetSummary(request), String.join(",", targetEntities),
                    0, true, latency, properties.getLlm().getSummarizerModel());
            return ResponseEntity.ok(cached.asCached(latency));
        }

        List<TraversalResult> results;
        try {
            results = traversalEngine.traverse(request.targets(), effectivePlan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        LinkedHashSet<String> entitiesTouched = new LinkedHashSet<>();
        int totalRows = 0;
        for (TraversalResult result : results) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("self", result.self());
            node.put("parents", result.parents());
            node.put("children", result.children());
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
            answer = summarizer.summarize(request.question(), data, catalog.instructionsFor(new ArrayList<>(entitiesTouched)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "summarizer call failed: " + e.getMessage()));
        }

        long latency = System.currentTimeMillis() - started;
        QueryResponse response = new QueryResponse(answer, request.targets(), data,
                new QueryResponse.Meta(false, Instant.now().toString(), properties.getLlm().getPlannerModel(),
                        properties.getLlm().getSummarizerModel(), effectivePlan.parentDepth(),
                        effectivePlan.childDepth(), latency));
        cache.put(cacheKey, response);
        auditLog.record(request.question(), targetSummary(request), String.join(",", entitiesTouched),
                totalRows, false, latency, properties.getLlm().getSummarizerModel());
        return ResponseEntity.ok(response);
    }

    private String targetSummary(QueryRequest request) {
        return request.targets().stream()
                .map(t -> t.entity() + ":" + t.id())
                .collect(Collectors.joining(","));
    }
}
