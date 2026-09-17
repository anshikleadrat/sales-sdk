package com.leadrat.aisdk.security;

import com.leadrat.aisdk.config.AiSdkProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AiSdkCorsFilter extends OncePerRequestFilter {

    private final AiSdkProperties properties;

    public AiSdkCorsFilter(AiSdkProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && isAllowed(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Max-Age", "600");
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String origin) {
        List<String> allowed = properties.getSecurity().getAllowedOrigins();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        return allowed.stream().anyMatch(candidate -> candidate.equals("*") || candidate.equalsIgnoreCase(origin));
    }
}
