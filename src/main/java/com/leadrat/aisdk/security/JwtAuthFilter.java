package com.leadrat.aisdk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String SUBJECT_ATTRIBUTE = "aiSdkSubject";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/ai-sdk",
            "/ai-sdk/setup",
            "/ai-sdk/auth/token",
            "/ai-sdk/auth",
            "/ai-sdk/configure",
            "/ai-sdk/console",
            "/ai-sdk/embed",
            "/ai-sdk/status");

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!path.startsWith("/ai-sdk")) {
            chain.doFilter(request, response);
            return;
        }
        if (isPublic(path, request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "Missing bearer token");
            return;
        }
        try {
            String subject = jwtService.verify(header.substring(7).trim()).getSubject();
            request.setAttribute(SUBJECT_ATTRIBUTE, subject);
        } catch (Exception e) {
            unauthorized(response, "Invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isPublic(String path, String method) {
        if (path.startsWith("/ai-sdk/assets")) {
            return true;
        }
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) {
                return "/ai-sdk/setup".equals(publicPath) || "/ai-sdk/auth/token".equals(publicPath) || "GET".equalsIgnoreCase(method);
            }
        }
        return false;
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
