package pl.kdtraining.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pl.kdtraining.infrastructure.security.JwtAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Locale POLISH = Locale.of("pl");
    // Local mapper for small error responses — independent of the application's Jackson configuration.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppConfig appConfig;
    private final Environment environment;
    @Nullable private final OAuth2UserService oAuth2UserService;
    @Nullable private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final MessageSource messageSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AppConfig appConfig, Environment environment,
                          ObjectProvider<OAuth2UserService> oAuth2UserService,
                          ObjectProvider<OAuth2SuccessHandler> oAuth2SuccessHandler,
                          MessageSource messageSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appConfig = appConfig;
        this.environment = environment;
        this.oAuth2UserService = oAuth2UserService.getIfAvailable();
        this.oAuth2SuccessHandler = oAuth2SuccessHandler.getIfAvailable();
        this.messageSource = messageSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (devProfile) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll();
                }
                auth.requestMatchers("/actuator/health").permitAll();
                auth.requestMatchers("/api/auth/**").permitAll()
                    // Public file serving: allow GET and HEAD. HEAD is read-only and used
                    // by crawlers/CDNs to preflight a file; matching only GET let HEAD fall
                    // through to anyRequest().authenticated() and return 401.
                    .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/files/**").permitAll();
                if (oAuth2UserService != null) {
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                }
                if (devProfile) {
                    auth.requestMatchers("/api/dev/**").permitAll();
                }
                auth.requestMatchers("/og/**").permitAll()
                    .requestMatchers("/sitemap.xml").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/user/**").authenticated()
                    .anyRequest().authenticated();
            });

        if (oAuth2UserService != null && oAuth2SuccessHandler != null) {
            http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
            );
        }

        return http
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String message = messageSource.getMessage("error.unauthorized", null, POLISH);
                    writeJsonError(response, 401, "UNAUTHORIZED", message);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String message = messageSource.getMessage("error.forbidden", null, POLISH);
                    writeJsonError(response, 403, "FORBIDDEN", message);
                })
            )
            .headers(headers -> headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        var origins = Arrays.stream(appConfig.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .toList();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeJsonError(HttpServletResponse response,
                                       int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(),
            Map.of("code", code, "message", message, "timestamp", Instant.now().toString()));
    }
}
