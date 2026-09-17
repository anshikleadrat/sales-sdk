package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MeetingReconciler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MeetingReconciler.class);
    private static final int BATCH = 50;

    private final AiSdkProperties properties;
    private final RecallHttp http;
    private final RecallCalendarService calendarService;
    private final GoogleCredentialStore credentialStore;
    private final MeetingStore meetingStore;
    private ScheduledExecutorService scheduler;

    public MeetingReconciler(AiSdkProperties properties, RecallHttp http, RecallCalendarService calendarService,
                             GoogleCredentialStore credentialStore, MeetingStore meetingStore) {
        this.properties = properties;
        this.http = http;
        this.calendarService = calendarService;
        this.credentialStore = credentialStore;
        this.meetingStore = meetingStore;
    }

    public void start() {
        int seconds = properties.getMeeting().getRecall().getReconcileSeconds();
        if (!properties.getMeeting().isActive() || seconds <= 0 || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-sdk-meeting-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::reconcile, seconds, seconds, TimeUnit.SECONDS);
    }

    public void reconcile() {
        if (!http.configured()) {
            return;
        }
        try {
            syncCalendar();
            scheduleMissingBots();
        } catch (Exception e) {
            log.warn("ai-sdk: meeting reconcile failed ({})", e.getMessage());
        }
    }

    private void syncCalendar() {
        GoogleCredential credential = credentialStore.findConnected().orElse(null);
        if (credential == null || credential.getRecallCalendarId() == null) {
            return;
        }
        try {
            calendarService.refreshStatus(credential.getRecallCalendarId());
            GoogleCredential fresh = credentialStore.findConnected().orElse(credential);
            if ("disconnected".equalsIgnoreCase(String.valueOf(fresh.getRecallStatus()))) {
                log.info("ai-sdk: re-registering disconnected recall calendar {}", fresh.getRecallCalendarId());
                calendarService.connect();
                return;
            }
            calendarService.syncCalendar(fresh.getRecallCalendarId(), fresh.getRecallSyncedAt());
            fresh.setRecallSyncedAt(Instant.now());
            credentialStore.save(fresh);
        } catch (Exception e) {
            log.warn("ai-sdk: recall calendar sync failed ({})", e.getMessage());
        }
    }

    private void scheduleMissingBots() {
        List<Meeting> pending;
        try {
            pending = meetingStore.findPending(Instant.now(), BATCH);
        } catch (Exception e) {
            log.warn("ai-sdk: recall reconcile query failed ({})", e.getMessage());
            return;
        }
        for (Meeting meeting : pending) {
            try {
                calendarService.scheduleBot(meeting);
            } catch (Exception e) {
                log.warn("ai-sdk: recall reconcile schedule failed for {} ({})", meeting.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
