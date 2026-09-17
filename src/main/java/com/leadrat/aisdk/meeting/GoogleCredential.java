package com.leadrat.aisdk.meeting;

import java.time.Instant;

public class GoogleCredential {

    private String googleEmail;
    private String refreshTokenEncrypted;
    private String scopes;
    private String recallCalendarId;
    private String recallStatus;
    private String recallError;
    private Instant recallSyncedAt;
    private Instant connectedAt;
    private Instant lastRefreshAt;
    private Instant revokedAt;

    public String getGoogleEmail() { return googleEmail; }
    public void setGoogleEmail(String googleEmail) { this.googleEmail = googleEmail; }
    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) { this.refreshTokenEncrypted = refreshTokenEncrypted; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public String getRecallCalendarId() { return recallCalendarId; }
    public void setRecallCalendarId(String recallCalendarId) { this.recallCalendarId = recallCalendarId; }
    public String getRecallStatus() { return recallStatus; }
    public void setRecallStatus(String recallStatus) { this.recallStatus = recallStatus; }
    public String getRecallError() { return recallError; }
    public void setRecallError(String recallError) { this.recallError = recallError; }
    public Instant getRecallSyncedAt() { return recallSyncedAt; }
    public void setRecallSyncedAt(Instant recallSyncedAt) { this.recallSyncedAt = recallSyncedAt; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public Instant getLastRefreshAt() { return lastRefreshAt; }
    public void setLastRefreshAt(Instant lastRefreshAt) { this.lastRefreshAt = lastRefreshAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
