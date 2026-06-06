package io.github.p4suta.webapp.app;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    @Test
    void setsTheDefensiveHeadersAndContinuesTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chained = {false};
        FilterChain chain = (req, res) -> chained[0] = true;

        new SecurityHeadersFilter().doFilter(request, response, chain);

        assertThat(chained[0]).isTrue();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("default-src 'self'")
                .contains("img-src 'self' data:") // data: favicon
                .contains("style-src 'self' 'unsafe-inline'") // progress-bar inline width
                .contains("frame-src 'self'") // same-origin PDF preview iframe
                .contains("frame-ancestors 'self'");
    }
}
