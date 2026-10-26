package pl.kdtraining.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private MessageSource messageSource;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(messageSource);
    }

    @Test
    void shouldAllowRequestBelowLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockAuthRequestsAfterLimit() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blockedResponse = null;
        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            blockedResponse = response;
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
        assertTrue(blockedResponse.getContentAsString().contains("TOO_MANY_REQUESTS"));
    }

    @Test
    void shouldAllowNormalBrowsingOfPublicEndpoints() throws ServletException, IOException {
        // Given: a visitor browsing the catalog — well below the anonymous ceiling
        MockHttpServletResponse response = null;
        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/events");
            request.setRemoteAddr("192.168.1.1");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // Then: never throttled
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockPublicRequestsAfterLimit() throws ServletException, IOException {
        // Given: an anonymous flood of the no-store catalog endpoint (every hit reaches the DB)
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blockedResponse = null;
        for (int i = 0; i <= 120; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/events");
            request.setRemoteAddr("10.0.0.9");
            blockedResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, blockedResponse, filterChain);
        }

        // Then: the 121st request in the window is rejected
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
        assertTrue(blockedResponse.getContentAsString().contains("TOO_MANY_REQUESTS"));
    }

    @Test
    void shouldRateLimitSitemapAndOgStubs() throws ServletException, IOException {
        // Given: the sitemap (three table scans) and the OG stubs share the anonymous bucket
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blockedResponse = null;
        for (int i = 0; i <= 120; i++) {
            // Alternating paths — one shared "public" bucket, not one per path
            String path = i % 2 == 0 ? "/sitemap.xml" : "/og/kadra/" + UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr("10.0.0.10");
            blockedResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, blockedResponse, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
    }

    /**
     * The health probe staying unlimited is a contract, not an accident: the container healthcheck
     * polls it from one address every few seconds, and a 429 would flap the container to unhealthy.
     * That is why the catch-all below stops at /api instead of covering every path.
     */
    @Test
    void shouldNotRateLimitTheHealthProbe() throws ServletException, IOException {
        MockHttpServletResponse response = null;
        for (int i = 0; i <= 200; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRemoteAddr("192.168.1.50");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        assertEquals(200, response.getStatus());
    }

    /**
     * Deny by default. A path under /api that no rule claims used to pass with no ceiling at all,
     * which meant a controller nobody remembered to add here started life completely unthrottled —
     * and nothing about the omission was visible from the controller.
     */
    @Test
    void shouldThrottleApiPathsNoRuleClaims() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 120; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/something-nobody-bucketed");
            request.setRemoteAddr("10.70.0.1");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    /**
     * The bare base carries no trailing slash, so a rule written as startsWith("/api/user/") would
     * miss it and drop the request into the generic bucket instead of the one it belongs to.
     */
    @Test
    void shouldCountABareBasePathIntoTheSameBucketAsItsSubPaths() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: the user bucket is spent through ordinary sub-paths
        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 40; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/me");
            request.setRemoteAddr("10.80.0.1");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());

        // When: a request arrives on the bare base
        MockHttpServletRequest bare = new MockHttpServletRequest("GET", "/api/user");
        bare.setRemoteAddr("10.80.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(bare, response, filterChain);

        // Then: it counts into the same exhausted bucket, not past the limiter
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
    }

    /** A neighbouring path must not be swallowed by a base it merely starts with. */
    @Test
    void shouldNotTreatALongerSiblingPathAsTheBase() {
        assertEquals("user", RateLimitFilter.bucketFor("/api/user"));
        assertEquals("default", RateLimitFilter.bucketFor("/api/users-export"));
    }

    /**
     * The escape hatch for load testing, where every request comes from one address and the limiter
     * would measure itself instead of the application. Off by default everywhere else.
     */
    @Test
    void shouldPassEverythingThroughWhenDisabled() throws ServletException, IOException {
        RateLimitFilter disabled = new RateLimitFilter(messageSource, false);

        MockHttpServletResponse response = null;
        for (int i = 0; i <= 200; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.90.0.1");
            response = new MockHttpServletResponse();
            disabled.doFilterInternal(request, response, filterChain);
        }

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldTrackDifferentBucketsSeparately() throws ServletException, IOException {
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("172.16.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest userRequest = new MockHttpServletRequest("GET", "/api/user/me");
        userRequest.setRemoteAddr("172.16.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(userRequest, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldUseXForwardedForHeader() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest differentIp = new MockHttpServletRequest("POST", "/api/auth/login");
        differentIp.setRemoteAddr("127.0.0.1");
        differentIp.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(differentIp, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldPreferCfConnectingIpOverSpoofedXForwardedFor() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Attacker rotates a spoofed X-Forwarded-For per request, but Cloudflare pins
        // CF-Connecting-IP to their real IP -> every request shares one bucket and gets limited.
        MockHttpServletResponse last = null;
        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("CF-Connecting-IP", "203.0.113.7");
            request.addHeader("X-Forwarded-For", "10." + i + "." + i + ".1");
            last = new MockHttpServletResponse();
            filter.doFilterInternal(request, last, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), last.getStatus());
    }

    /**
     * The one place the /api/user ceiling is stated as a number, so raising or lowering it has to be
     * a deliberate edit here. The other tests in this class spend this bucket to prove something else
     * and would happily keep passing against a different limit.
     */
    @Test
    void shouldAllowFortyUserRequestsAndBlockTheFortyFirst() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        for (int i = 0; i < 40; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/me");
            request.setRemoteAddr("10.85.0.1");
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilterInternal(request, ok, filterChain);
            assertEquals(200, ok.getStatus(), "request " + i + " is within the user limit");
        }

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/me");
        request.setRemoteAddr("10.85.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(request, blocked, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    /**
     * Reads and writes under /api/user share one counter and cannot be split by path — GET and POST
     * /api/user/enrollments are the same URI. So the ceiling itself has to leave room for the traffic
     * that arrives without anyone asking for it: at 20/min, a session that had merely browsed was
     * refused the booking. Twenty-one reads is exactly the case that used to fail.
     */
    @Test
    void shouldNotSpendTheBookingLimitOnOrdinaryProfileReads() throws ServletException, IOException {
        for (int i = 0; i < 21; i++) {
            MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/user/me");
            read.setRemoteAddr("10.86.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(read, response, filterChain);
            assertEquals(200, response.getStatus(), "read " + i + " is within the user limit");
        }

        MockHttpServletRequest booking = new MockHttpServletRequest("POST", "/api/user/enrollments");
        booking.setRemoteAddr("10.86.0.1");
        MockHttpServletResponse bookingResponse = new MockHttpServletResponse();
        filter.doFilterInternal(booking, bookingResponse, filterChain);

        assertEquals(200, bookingResponse.getStatus(), "browsing must not block signing up");
    }

    @Test
    void shouldAllowHigherLimitForAdminEndpoints() throws ServletException, IOException {
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/instructors");
            request.setRemoteAddr("10.10.10.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void shouldGiveTrainingCalendarItsOwnBudgetWhenUserBucketIsExhausted() throws ServletException, IOException {
        // Given: the generic /api/user/ bucket (40/min) is fully spent
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");
        MockHttpServletResponse userResponse = null;
        for (int i = 0; i <= 40; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/me");
            request.setRemoteAddr("10.30.0.1");
            userResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, userResponse, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), userResponse.getStatus());

        // When: the same client opens their training calendar
        MockHttpServletRequest calendar = new MockHttpServletRequest("GET", "/api/user/my-training/calendar");
        calendar.setRemoteAddr("10.30.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(calendar, response, filterChain);

        // Then: it is unaffected — the prefix must be matched before the generic /api/user/ branch
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockTrainingCalendarOnlyAfterItsOwnHigherLimit() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: 120 requests fit in the window (well above the 20 of the generic user bucket)
        for (int i = 0; i < 120; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/my-training/calendar");
            request.setRemoteAddr("10.30.0.2");
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilterInternal(request, ok, filterChain);
            assertEquals(200, ok.getStatus());
        }

        // When: one more arrives
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/my-training/summary");
        request.setRemoteAddr("10.30.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        // Then: rejected — and note it shares one bucket with the other my-training paths
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
    }

    @Test
    void shouldRationPhotoUploadsFarBelowTheCalendarCeiling() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: twelve uploads fit — nothing like the 120 the calendar itself gets
        for (int i = 0; i < 12; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/my-training/photos");
            request.setRemoteAddr("10.40.0.1");
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilterInternal(request, ok, filterChain);
            assertEquals(200, ok.getStatus());
        }

        MockHttpServletRequest thirteenth = new MockHttpServletRequest("POST", "/api/user/my-training/photos");
        thirteenth.setRemoteAddr("10.40.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(thirteenth, blocked, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    /**
     * The trap this file warns about: the upload prefix has to be tested before the my-training one
     * in BOTH resolve methods. Matched in the wrong order, uploads would land on the 120/min counter
     * — or worse, browsing the calendar would be rationed at 12/min and nobody would notice until a
     * client could not scroll through their own plan.
     */
    @Test
    void shouldNotLetPhotoUploadsEatTheCalendarBudget() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: the upload bucket is spent
        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 12; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/my-training/photos");
            request.setRemoteAddr("10.40.0.2");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());

        // When: the same client keeps browsing, and reads a photo they already have
        MockHttpServletRequest calendar = new MockHttpServletRequest("GET", "/api/user/my-training/calendar");
        calendar.setRemoteAddr("10.40.0.2");
        MockHttpServletResponse calendarResponse = new MockHttpServletResponse();
        filter.doFilterInternal(calendar, calendarResponse, filterChain);

        MockHttpServletRequest read = new MockHttpServletRequest(
            "GET", "/api/user/my-training/comments/" + UUID.randomUUID() + "/photo");
        read.setRemoteAddr("10.40.0.2");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        filter.doFilterInternal(read, readResponse, filterChain);

        // Then: both are untouched — only the multipart writes are rationed
        assertEquals(200, calendarResponse.getStatus());
        assertEquals(200, readResponse.getStatus());
    }

    @Test
    void shouldRationCoachPhotoUploadsSeparatelyFromTheAdminBucket() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 12; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/training-photos");
            request.setRemoteAddr("10.40.0.3");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());

        // The rest of the admin panel keeps its own 60/min
        MockHttpServletRequest admin = new MockHttpServletRequest("GET", "/api/admin/instructors");
        admin.setRemoteAddr("10.40.0.3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(admin, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    /**
     * The avatar is the third multipart endpoint in the app and costs exactly what the other two cost:
     * up to 10 MB read into memory before any handler can refuse it. It used to count against the
     * ordinary user ceiling, so the ration that exists to protect a 384 MB container guarded two of
     * the three doors into the same operation.
     */
    @Test
    void shouldRationAvatarUploadsWithTheOtherFileUploads() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 12; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/me/avatar");
            request.setRemoteAddr("10.87.0.1");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());

        // The rest of the account keeps its own 40/min — this is a narrower rule, not a tighter user bucket.
        MockHttpServletRequest profile = new MockHttpServletRequest("GET", "/api/user/me");
        profile.setRemoteAddr("10.87.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(profile, response, filterChain);

        assertEquals(200, response.getStatus(), "uploading a picture must not throttle the profile");
    }

    /**
     * One ration for all file uploads, not three ceilings that happen to share a number: a client that
     * has spent the budget on training photos may not turn around and spend it again on avatars.
     */
    @Test
    void shouldCountAvatarsAndTrainingPhotosIntoOneUploadBudget() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        for (int i = 0; i < 12; i++) {
            MockHttpServletRequest photo = new MockHttpServletRequest("POST", "/api/user/my-training/photos");
            photo.setRemoteAddr("10.87.0.2");
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilterInternal(photo, ok, filterChain);
            assertEquals(200, ok.getStatus(), "photo " + i + " is within the upload limit");
        }

        MockHttpServletRequest avatar = new MockHttpServletRequest("POST", "/api/user/me/avatar");
        avatar.setRemoteAddr("10.87.0.2");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(avatar, blocked, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    @Test
    void shouldReturnJsonErrorResponse() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRemoteAddr("10.20.30.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("10.20.30.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"message\":\"Zbyt wiele żądań\""));
        // A client told to back off has to be told for how long, or it just retries in a loop
        assertEquals("60", response.getHeader("Retry-After"));
    }

    /**
     * Public file streaming used to match no prefix at all, so it was served without any ceiling.
     * Cloudflare caches the hits; requests for names that are not on disk miss the edge every time
     * and land on the origin.
     */
    @Test
    void shouldBlockFileStreamingAfterItsOwnCeiling() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: a gallery's worth of images is nowhere near the ceiling
        for (int i = 0; i < 240; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/eventtypephotos/" + UUID.randomUUID() + ".jpg");
            request.setRemoteAddr("10.50.0.1");
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilterInternal(request, ok, filterChain);
            assertEquals(200, ok.getStatus());
        }

        MockHttpServletRequest flood = new MockHttpServletRequest("GET", "/api/files/avatars/" + UUID.randomUUID() + ".jpg");
        flood.setRemoteAddr("10.50.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(flood, blocked, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    @Test
    void shouldNotLetFileStreamingEatTheAnonymousBucket() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: the file bucket is spent
        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 240; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/avatars/x.jpg");
            request.setRemoteAddr("10.50.0.2");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());

        // Then: the catalog the same visitor is browsing still answers — separate counter
        MockHttpServletRequest catalog = new MockHttpServletRequest("GET", "/api/public/events");
        catalog.setRemoteAddr("10.50.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(catalog, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldCountGoogleSignInAgainstTheCredentialBucket() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Given: the OAuth handshake shares the 15/min auth ceiling instead of sitting outside every bucket
        MockHttpServletResponse blocked = null;
        for (int i = 0; i <= 15; i++) {
            String path = i % 2 == 0 ? "/oauth2/authorization/google" : "/login/oauth2/code/google";
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr("10.60.0.1");
            blocked = new MockHttpServletResponse();
            filter.doFilterInternal(request, blocked, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    /**
     * Spring routes on the DECODED path, so {@code /%61pi/auth/login} ({@code %61} = "a") reaches
     * the login controller. A limiter matching the raw URI counted it in the default bucket
     * (120/min) instead of auth (15/min) — eight times the password guesses (audit, 2026-10-26).
     * Cloudflare normalises such URLs and the origin only accepts Cloudflare, so this is a second
     * line, not the first; the test pins both halves: that Spring does route it, and that the
     * limiter counts it where Spring sends it.
     */
    @Test
    void shouldCountAPathInTheBucketSpringRoutesItTo() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Not `//api/auth/login`: Spring does not route a double slash to the endpoint at all.
        for (String path : List.of("/%61pi/auth/login", "/api/auth/login;x=1")) {
            MockHttpServletRequest routed = new MockHttpServletRequest("POST", path);
            assertTrue(new PathPatternParser().parse("/api/auth/login")
                    .matches(ServletRequestPathUtils.parseAndCache(routed).pathWithinApplication()),
                "Spring routes " + path + " to the login endpoint");

            RateLimitFilter fresh = new RateLimitFilter(messageSource);
            MockHttpServletResponse last = null;
            for (int i = 0; i <= 15; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
                request.setRemoteAddr("198.51.100.9");
                last = new MockHttpServletResponse();
                fresh.doFilterInternal(request, last, filterChain);
            }
            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), last.getStatus(),
                path + " must spend the auth limit, not the default one");
        }
    }
}
