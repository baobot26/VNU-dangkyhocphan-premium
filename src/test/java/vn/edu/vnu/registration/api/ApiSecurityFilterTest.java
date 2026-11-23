package vn.edu.vnu.registration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.vnu.registration.config.BackendConfig;
import vn.edu.vnu.registration.data.metrics.QueryTimer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiSecurityFilterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void rateLimitedHttpResponseHasRetryAfterAndErrorEnvelope() throws Exception {
        var filter = new ApiSecurityFilter(BackendConfig.missingTokenGateway(), new MemoryRateLimiter(clock, 1, 1, 1), new AdmissionController(0, 0, 0), json, "127.0.0.1", mock(QueryTimer.class));
        var first = new MockHttpServletResponse();
        filter.doFilter(request(null), first, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        assertThat(first.getStatus()).isEqualTo(204);
        var second = new MockHttpServletResponse();
        filter.doFilter(request(null), second, (req, res) -> fail("Over-limit request must not reach controller"));
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
        assertThat(json.readTree(second.getContentAsString()).path("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(second.getHeader("X-Request-ID")).isNotBlank();
    }

    @Test
    void unconfiguredGatewayFailsClosedWithNoSecurityIdentity() throws Exception {
        SecurityContextHolder.clearContext();
        try {
            var filter = new ApiSecurityFilter(BackendConfig.missingTokenGateway(), new MemoryRateLimiter(clock, 10, 10, 10), new AdmissionController(0, 0, 0), json, "127.0.0.1", mock(QueryTimer.class));
            var response = new MockHttpServletResponse();
            filter.doFilter(request("Bearer anything"), response, (req, res) -> fail("Unverified bearer must not reach controller"));
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(json.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("AUTH_NOT_CONFIGURED");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void healthAndAdminPutBypassAdmission() throws Exception {
        var admission = new AdmissionController(0, 0, 1);
        var filter = new ApiSecurityFilter(BackendConfig.missingTokenGateway(), new MemoryRateLimiter(clock, 10, 10, 10), admission, json, "127.0.0.1", mock(QueryTimer.class));

        var health = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/v1/health", null), health, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        assertThat(health.getStatus()).isEqualTo(204);
        assertThat(admission.availablePermits(AdmissionController.Group.READ)).isEqualTo(1);

        var admin = new MockHttpServletResponse();
        filter.doFilter(request("PUT", "/api/v1/admin/period", null), admin, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        assertThat(admin.getStatus()).isEqualTo(204);
        assertThat(admission.availablePermits(AdmissionController.Group.READ)).isEqualTo(1);
        assertThat(admission.availablePermits(AdmissionController.Group.WRITE)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void rejectedRequestDoesNotReleaseAnotherRequestsPermit() throws Exception {
        var admission = new AdmissionController(0, 0, 1);
        var filter = new ApiSecurityFilter(BackendConfig.missingTokenGateway(), new MemoryRateLimiter(clock, 10, 10, 10), admission, json, "127.0.0.1", mock(QueryTimer.class));
        var rejected = new MockHttpServletResponse();
        var completed = new MockHttpServletResponse();

        filter.doFilter(request(null), completed, (req, res) -> {
            filter.doFilter(request(null), rejected, (nestedReq, nestedRes) -> fail("Rejected request must not reach controller"));
            assertThat(rejected.getStatus()).isEqualTo(429);
            assertThat(admission.availablePermits(AdmissionController.Group.READ)).isZero();
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204);
        });

        assertThat(completed.getStatus()).isEqualTo(204);
        assertThat(admission.availablePermits(AdmissionController.Group.READ)).isEqualTo(1);
    }

    private MockHttpServletRequest request(String authorization) {
        return request("GET", "/api/v1/me", authorization);
    }

    private MockHttpServletRequest request(String method, String path, String authorization) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }
}
