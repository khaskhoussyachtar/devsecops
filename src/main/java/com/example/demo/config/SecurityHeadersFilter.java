package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // Prevent MIME-type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // XSS protection
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Clickjacking
        response.setHeader("X-Frame-Options", "SAMEORIGIN");

        // HSTS
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // CSP - improved with fallback
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; object-src 'none';");

        // Referrer policy
        response.setHeader("Referrer-Policy", "no-referrer");

        // Permissions policy
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        // Cache control for sensitive content
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // Spectre mitigation
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");

        // Enforce Secure & HttpOnly on cookies
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                cookie.setHttpOnly(true);
                cookie.setSecure(true);
                cookie.setPath("/");
                
            }
        }

        chain.doFilter(request, response);
    }
}
