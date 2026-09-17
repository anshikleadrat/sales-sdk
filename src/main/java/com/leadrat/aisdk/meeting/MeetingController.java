package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.meeting.dto.CreateMeetingRequest;
import com.leadrat.aisdk.meeting.dto.DiscussionResponse;
import com.leadrat.aisdk.meeting.dto.MeetingResponse;
import com.leadrat.aisdk.meeting.dto.UpdateMeetingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-sdk/meetings")
public class MeetingController {

    private final AiSdkProperties properties;
    private final MeetingService meetingService;
    private final GoogleCalendarClient calendarClient;
    private final RecallHttp recallHttp;
    private final GoogleCredentialStore credentialStore;

    public MeetingController(AiSdkProperties properties, MeetingService meetingService,
                             GoogleCalendarClient calendarClient, RecallHttp recallHttp,
                             GoogleCredentialStore credentialStore) {
        this.properties = properties;
        this.meetingService = meetingService;
        this.calendarClient = calendarClient;
        this.recallHttp = recallHttp;
        this.credentialStore = credentialStore;
    }

    @PostMapping
    public ResponseEntity<?> schedule(@RequestBody CreateMeetingRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(MeetingResponse.from(meetingService.schedule(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<MeetingResponse>> list(@RequestParam String leadId) {
        return ResponseEntity.ok(meetingService.byLead(leadId).stream().map(MeetingResponse::from).toList());
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<?> get(@PathVariable String meetingId) {
        return meetingService.find(meetingId)
                .<ResponseEntity<?>>map(meeting -> ResponseEntity.ok(MeetingResponse.from(meeting)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "meeting not found")));
    }

    @PatchMapping("/{meetingId}")
    public ResponseEntity<?> update(@PathVariable String meetingId, @RequestBody UpdateMeetingRequest request) {
        try {
            return ResponseEntity.ok(MeetingResponse.from(meetingService.update(meetingId, request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String meetingId) {
        try {
            return ResponseEntity.ok(MeetingResponse.from(meetingService.cancel(meetingId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/complete")
    public ResponseEntity<?> complete(@PathVariable String meetingId) {
        try {
            return ResponseEntity.ok(MeetingResponse.from(meetingService.complete(meetingId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{meetingId}/discussions")
    public ResponseEntity<List<DiscussionResponse>> discussions(@PathVariable String meetingId) {
        return ResponseEntity.ok(meetingService.discussions(meetingId).stream()
                .map(DiscussionResponse::from).toList());
    }

    @GetMapping("/discussions")
    public ResponseEntity<List<DiscussionResponse>> leadDiscussions(@RequestParam String leadId,
                                                                   @RequestParam(required = false) Integer limit) {
        int cap = limit == null || limit <= 0
                ? properties.getMeeting().getMaxDiscussionsPerLead() : limit;
        return ResponseEntity.ok(meetingService.leadDiscussions(leadId, cap).stream()
                .map(DiscussionResponse::from).toList());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        GoogleCredential credential = credentialStore.findConnected().orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.getMeeting().isActive());
        body.put("googleEnabled", calendarClient.enabled());
        body.put("googleConnected", credential != null);
        body.put("googleEmail", credential == null ? null : credential.getGoogleEmail());
        body.put("canGenerateLinks", calendarClient.canGenerateLinks());
        body.put("botEnabled", recallHttp.configured());
        body.put("recallConnected", credential != null && credential.getRecallCalendarId() != null);
        body.put("recallStatus", credential == null ? null : credential.getRecallStatus());
        body.put("recallError", credential == null ? null : credential.getRecallError());
        body.put("leadEntity", properties.getMeeting().getLeadEntity());
        return ResponseEntity.ok(body);
    }
}
