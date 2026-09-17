package com.leadrat.aisdk.config;

public class ReadOnlyViolationException extends RuntimeException {

    public ReadOnlyViolationException(String message) {
        super(message);
    }
}
