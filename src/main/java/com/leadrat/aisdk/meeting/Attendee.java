package com.leadrat.aisdk.meeting;

public record Attendee(String email, String displayName, boolean optional) {

    public Attendee(String email, String displayName) {
        this(email, displayName, false);
    }
}
