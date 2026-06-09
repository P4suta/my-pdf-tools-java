package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The same-origin CSRF guard: cross-origin state-changing requests are rejected, while safe
 * methods, same-origin requests, and header-less (non-browser) requests pass.
 */
class SameOriginCsrfFilterTest {

    private static final String HOST = "pdfbook.example.com";

    private final SameOriginCsrfFilter filter = new SameOriginCsrfFilter();

    private static MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/jobs");
        request.addHeader("Host", HOST);
        return request;
    }

    private MockHttpServletResponse pass(MockHttpServletRequest request)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    private boolean reached(MockHttpServletRequest request) throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null; // the chain advanced only if the request was allowed
    }

    @Test
    void allowsSafeMethodsRegardlessOfOrigin() throws Exception {
        MockHttpServletRequest get = request("GET");
        get.addHeader("Origin", "https://evil.example.com");

        assertThat(reached(get)).isTrue();
    }

    @Test
    void allowsRequestsWithNoOriginOrReferer() throws Exception {
        // Non-browser client (curl, the smoke tests): not a CSRF vector.
        assertThat(reached(request("POST"))).isTrue();
    }

    @Test
    void allowsSameOriginPost() throws Exception {
        MockHttpServletRequest post = request("POST");
        post.addHeader("Origin", "https://" + HOST);

        assertThat(reached(post)).isTrue();
    }

    @Test
    void allowsSameOriginByRefererWhenOriginAbsent() throws Exception {
        MockHttpServletRequest post = request("POST");
        post.addHeader("Referer", "https://" + HOST + "/app");

        assertThat(reached(post)).isTrue();
    }

    @Test
    void rejectsCrossOriginPost() throws Exception {
        MockHttpServletRequest post = request("POST");
        post.addHeader("Origin", "https://evil.example.com");

        MockHttpServletResponse response = pass(post);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
