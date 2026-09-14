package com.genchat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Disable proxy/servlet buffering for agent SSE endpoints so chunks reach the browser promptly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SseStreamHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.contains("/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-cache, no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        try {
            response.setBufferSize(512);
        } catch (IllegalStateException ignored) {
            // response already committed
        }
        filterChain.doFilter(request, response);
    }
}
