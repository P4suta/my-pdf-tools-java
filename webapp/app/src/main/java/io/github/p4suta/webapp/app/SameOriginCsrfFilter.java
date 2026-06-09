package io.github.p4suta.webapp.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects cross-origin state-changing requests, a hand-rolled CSRF guard (no Spring Security on the
 * classpath). It matters specifically under the intended reverse-proxy + HTTP Basic deployment:
 * Basic auth makes the browser auto-attach credentials to every same-origin request, so it does NOT
 * defend against CSRF — and {@code POST /api/v1/jobs} is {@code multipart/form-data}, a CORS
 * "simple request" that triggers no preflight, so a malicious page could otherwise fire conversions
 * cross-site with the user's credentials.
 *
 * <p>Follows the OWASP "verify the Origin/Referer against the target host" pattern: on an unsafe
 * method, if the browser supplied an {@code Origin} (or, failing that, a {@code Referer}) its host
 * must equal the host the request was addressed to ({@code Host}). A request with neither header is
 * allowed — CSRF requires a browser replaying ambient credentials, and such a browser always sends
 * one of them; non-browser clients (curl, the smoke tests, server-to-server) are unaffected. The
 * embedded SPA is same-origin, so legitimate requests always pass.
 */
@Component
public class SameOriginCsrfFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (UNSAFE_METHODS.contains(request.getMethod()) && !isSameOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "cross-origin request rejected");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSameOrigin(HttpServletRequest request) {
        @Nullable String source = request.getHeader("Origin");
        if (source == null) {
            source = request.getHeader("Referer");
        }
        if (source == null) {
            // No browser-supplied origin: not a CSRF vector (see class doc). Let it through.
            return true;
        }
        @Nullable String sourceHost = hostOf(source);
        // The Host header is an authority (host[:port]); parse it as the authority of a relative
        // URI so host[:port] and bracketed IPv6 are handled the same way as the source URL.
        @Nullable String targetHost = hostOf("//" + request.getHeader("Host"));
        return sourceHost != null && sourceHost.equalsIgnoreCase(targetHost);
    }

    private static @Nullable String hostOf(@Nullable String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return URI.create(uri).getHost();
        } catch (IllegalArgumentException e) {
            return null; // malformed header → treat as no usable host (rejected when source
            // present)
        }
    }
}
