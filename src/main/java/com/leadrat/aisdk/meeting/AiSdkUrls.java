package com.leadrat.aisdk.meeting;

import jakarta.servlet.http.HttpServletRequest;

public final class AiSdkUrls {

    private AiSdkUrls() {
    }

    public static String externalBase(HttpServletRequest request) {
        String scheme = firstValue(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null) {
            scheme = request.getScheme();
        }
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (host == null) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && port != ("https".equals(scheme) ? 443 : 80)) {
                host = host + ":" + port;
            }
        }
        String context = request.getContextPath();
        return scheme + "://" + host + (context == null ? "" : context);
    }

    private static String firstValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        String value = comma < 0 ? header : header.substring(0, comma);
        return value.trim().isEmpty() ? null : value.trim();
    }
}
