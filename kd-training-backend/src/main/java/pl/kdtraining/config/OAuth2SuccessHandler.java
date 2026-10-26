package pl.kdtraining.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.infrastructure.security.JwtService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@org.springframework.context.annotation.Profile("oauth2")
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.site-url:http://localhost:5175}")
    private String frontendUrl;

    private final JwtService jwtService;
    private final AuthTokenRepository authTokenRepository;

    public OAuth2SuccessHandler(JwtService jwtService, AuthTokenRepository authTokenRepository) {
        this.jwtService = jwtService;
        this.authTokenRepository = authTokenRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        String refreshTokenHash = jwtService.hashToken(refreshToken);
        Instant refreshExpiration = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());
        AuthToken storedRefreshToken = new AuthToken(user, refreshTokenHash, TokenType.REFRESH_TOKEN, refreshExpiration);
        authTokenRepository.save(storedRefreshToken);

        String baseUrl = frontendUrl.split(",")[0].trim();
        String targetUrl = UriComponentsBuilder.fromUriString(baseUrl + "/oauth-callback")
            .fragment(tokenFragment(accessToken, refreshToken))
            .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * The tokens travel in the URL fragment, never the query string.
     *
     * <p>A refresh token is good for seven days, and a query string is written down everywhere: the
     * browser's history, nginx's access log, Cloudflare's log, and the {@code Referer} of anything
     * the callback page happens to load. All of those outlive the session they belong to, and none
     * of them are places anybody would think to look for a credential.
     *
     * <p>A fragment is never sent to a server, so it reaches the page and stops there. That is the
     * whole trick, and it costs one thing worth stating: the page must read
     * {@code window.location.hash} rather than the query, and wipe it once the tokens are stored —
     * see {@code OAuthCallbackPage}. This is still a handover through the address bar, which is what
     * a redirect-based flow gives you; the proper fix is a one-time code exchanged over POST, and
     * that is a larger change than this one.
     */
    private String tokenFragment(String accessToken, String refreshToken) {
        return "accessToken=" + encode(accessToken)
            + "&refreshToken=" + encode(refreshToken)
            + "&expiresIn=" + jwtService.getAccessTokenExpirationSeconds();
    }

    /**
     * JWTs are base64url plus dots, so nothing here actually needs escaping today — encoded anyway,
     * because the day the token format changes is not the day to remember this.
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
