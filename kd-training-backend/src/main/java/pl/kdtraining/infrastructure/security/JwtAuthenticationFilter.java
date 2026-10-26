package pl.kdtraining.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Cache<UUID, User> userCache;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();
    }

    public void evictUser(UUID userId) {
        userCache.invalidate(userId);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());

        // One parse, not three. Validating, checking the token type and reading the subject each used
        // to verify the signature and decode the claims again, on every single request.
        Claims claims = jwtService.readClaims(jwt);

        if (claims == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!JwtService.isAccessToken(claims)) {
            log.debug("Token is not an access token");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = JwtService.userIdOf(claims);
            User user = userCache.get(userId, id -> userRepository.findById(id).orElse(null));

            if (user == null) {
                log.debug("User not found for JWT token");
                filterChain.doFilter(request, response);
                return;
            }

            JwtAuthenticatedUser jwtUser = new JwtAuthenticatedUser(user);
            List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                jwtUser, null, authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT authentication successful for user: {}", user.getEmail());

        } catch (Exception e) {
            log.debug("Failed to process JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
