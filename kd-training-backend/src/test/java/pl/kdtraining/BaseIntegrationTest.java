package pl.kdtraining;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.security.JwtService;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@org.springframework.test.context.jdbc.Sql(scripts = "/cleanup.sql", executionPhase = org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("kdtraining_test")
            .withUsername("test")
            .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha");
        registry.add("app.admin.email", () -> "admin@kdtraining.test");
        registry.add("app.storage.root", () -> System.getProperty("java.io.tmpdir") + "/kd-training-test-uploads");
        registry.add("app.base-url", () -> "http://localhost:8083");
        registry.add("app.site-url", () -> "http://localhost:5175");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5175");
    }

    // Never call the real HIBP API from tests: the mock's isPwned(...) returns false by default.
    @MockitoBean
    protected pl.kdtraining.infrastructure.security.PwnedPasswordChecker pwnedPasswordChecker;

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    protected String createUserAndGetToken(String email, String firstName, String lastName, UserRole role) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, firstName, lastName, null);
            u.setPasswordHash("$2a$12$dummyhash");
            u.setRole(role);
            u.markEmailVerified();
            return userRepository.save(u);
        });
        return jwtService.generateAccessToken(user);
    }

    protected String adminToken() {
        return createUserAndGetToken("testadmin@kdtraining.test", "Admin", "Testowy", UserRole.ADMIN);
    }

    protected String userToken() {
        return createUserAndGetToken("testuser@kdtraining.test", "User", "Testowy", UserRole.USER);
    }

    protected UUID adminUserId() {
        return userRepository.findByEmail("testadmin@kdtraining.test")
            .orElseThrow().getId();
    }

    protected UUID regularUserId() {
        return userRepository.findByEmail("testuser@kdtraining.test")
            .orElseThrow().getId();
    }
}
