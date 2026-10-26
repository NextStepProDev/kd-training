package pl.kdtraining.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.infrastructure.security.JwtService;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Where the tokens end up after Google sends somebody back.
 *
 * <p>The subject here is the shape of the redirect, not the sign-in itself: a refresh token lives for
 * seven days, and the difference between the query string and the fragment decides whether it also
 * lives in nginx's log, Cloudflare's log and the browser's history.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    private static final String SITE = "https://kdtraining.example";

    @Mock private JwtService jwtService;
    @Mock private AuthTokenRepository authTokenRepository;

    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(jwtService, authTokenRepository);
        ReflectionTestUtils.setField(handler, "frontendUrl", SITE);
        when(jwtService.generateAccessToken(any())).thenReturn("access.token.value");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh.token.value");
        when(jwtService.hashToken(any())).thenReturn("hash");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }

    private String redirectAfterLogin() throws Exception {
        User user = new User("kasia@example.com", "Kasia", "Nowak", null);
        var delegate = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), java.util.Map.of("sub", "1"), "sub");
        var principal = new CustomOAuth2User(delegate, user);
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response,
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        return response.getRedirectedUrl();
    }

    @Test
    void shouldHandTheTokensOverInTheFragmentAndNotTheQueryString() throws Exception {
        String redirect = redirectAfterLogin();

        URI uri = URI.create(redirect);
        // The query is what gets written down by every proxy on the way. It must be empty.
        assertNull(uri.getQuery(), "tokens must not travel in the query string: " + redirect);
        assertEquals("/oauth-callback", uri.getPath());

        String fragment = uri.getFragment();
        assertNotNull(fragment);
        assertTrue(fragment.contains("accessToken=access.token.value"), fragment);
        assertTrue(fragment.contains("refreshToken=refresh.token.value"), fragment);
        assertTrue(fragment.contains("expiresIn=900"), fragment);
    }

    @Test
    void shouldSendPeopleBackToTheConfiguredSiteAndNowhereElse() throws Exception {
        // The callback address comes from configuration, never from the request — the same reason
        // redirect-uri is pinned in application-oauth2.yml instead of using {baseUrl}.
        assertTrue(redirectAfterLogin().startsWith(SITE + "/oauth-callback"));
    }

    @Test
    void shouldStoreTheRefreshTokenSoItCanBeRevoked() throws Exception {
        redirectAfterLogin();

        verify(authTokenRepository).save(any(AuthToken.class));
    }
}
