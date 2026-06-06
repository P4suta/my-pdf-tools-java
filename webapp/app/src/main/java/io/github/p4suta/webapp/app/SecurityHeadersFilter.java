package io.github.p4suta.webapp.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds defensive HTTP response headers to every response. Registered automatically because it is a
 * {@code Filter} {@code @Component}. No Spring Security on the classpath (auth is a separate
 * concern), so these are set by hand.
 *
 * <p>The CSP is tuned to the self-hosted SPA and verified against it: everything is same-origin
 * except the {@code data:} favicon, and the done view previews the result by embedding {@code
 * /api/.../result} in a same-origin {@code <iframe>} (hence {@code frame-src 'self'} and {@code
 * X-Frame-Options: SAMEORIGIN}, NOT {@code DENY}). {@code style-src 'unsafe-inline'} is
 * load-bearing: the progress bars set their width with an inline style ({@code style:width={...}}
 * in App.svelte), so removing it silently breaks the progress animation.
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
