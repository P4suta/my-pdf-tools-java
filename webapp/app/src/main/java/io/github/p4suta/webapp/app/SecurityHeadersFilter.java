package io.github.p4suta.webapp.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds defensive HTTP response headers to every response. Registered automatically as a {@code
 * Filter} {@code @Component}. No Spring Security on the classpath, so these are set by hand.
 *
 * <p>The CSP fits the self-hosted SPA: everything is same-origin except the {@code data:} favicon.
 * The done view previews the result in a same-origin {@code <iframe>}, so {@code frame-src 'self'}
 * and {@code X-Frame-Options: SAMEORIGIN} (not {@code DENY}). {@code style-src 'unsafe-inline'} is
 * required because the progress bars set their width with an inline style ({@code
 * style:width={...}} in App.svelte).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                    + "script-src 'self'; frame-src 'self'; object-src 'none'; base-uri 'self'; "
                    + "form-action 'self'; frame-ancestors 'self'";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader(
                "Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), browsing-topics=()");
        chain.doFilter(request, response);
    }
}
