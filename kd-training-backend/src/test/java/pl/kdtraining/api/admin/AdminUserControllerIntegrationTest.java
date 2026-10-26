package pl.kdtraining.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserControllerIntegrationTest extends BaseIntegrationTest {

    // app.admin.email in BaseIntegrationTest = "admin@kdtraining.test" → super-admin from .env
    private String superAdminToken() {
        return createUserAndGetToken("admin@kdtraining.test", "Env", "Admin", UserRole.ADMIN);
    }

    private User createUser(String email, String firstName, String lastName, UserRole role) {
        User u = new User(email, firstName, lastName, "123456789");
        u.setPasswordHash("$2a$12$dummyhash");
        u.setRole(role);
        u.markEmailVerified();
        return userRepository.save(u);
    }

    // --- security ---

    @Test
    void shouldRejectListForAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectListForRegularUser() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isForbidden());
    }

    // --- list / search ---

    @Test
    void shouldListUsers() throws Exception {
        createUser("alice@test.com", "Alicja", "Nowak", UserRole.USER);

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void shouldSearchUsersByPhrase() throws Exception {
        createUser("alice@test.com", "Alicja", "Kowalska", UserRole.USER);
        createUser("bob@test.com", "Robert", "Zieliński", UserRole.USER);
        adminToken();

        mockMvc.perform(get("/api/admin/users")
                .param("search", "kowal")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].email").value("alice@test.com"));
    }

    @Test
    void shouldSortUsersByNameAscending() throws Exception {
        createUser("z@test.com", "Zenon", "Zalewski", UserRole.USER);
        createUser("a@test.com", "Adam", "Adamski", UserRole.USER);
        createUser("n@test.com", "Natalia", "Nowak", UserRole.USER);

        mockMvc.perform(get("/api/admin/users")
                .param("sort", "name").param("direction", "asc")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].lastName").value("Adamski"));
    }

    @Test
    void shouldPaginateUsers() throws Exception {
        for (int i = 0; i < 5; i++) {
            createUser("user" + i + "@test.com", "Imie" + i, "Nazwisko" + i, UserRole.USER);
        }

        mockMvc.perform(get("/api/admin/users")
                .param("page", "0").param("size", "2")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            // 5 created + testadmin from adminToken() = 6 → 3 pages of 2
            .andExpect(jsonPath("$.totalElements").value(6))
            .andExpect(jsonPath("$.totalPages").value(3));
    }

    // --- detail ---

    @Test
    void shouldReturnUserDetail() throws Exception {
        User u = createUser("detail@test.com", "Jan", "Kowalski", UserRole.USER);

        mockMvc.perform(get("/api/admin/users/" + u.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("detail@test.com"))
            .andExpect(jsonPath("$.firstName").value("Jan"));
    }

    @Test
    void shouldReturnNotFoundForMissingUserDetail() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound());
    }

    // --- roles ---

    @Test
    void shouldPromoteUserBySuperAdmin() throws Exception {
        User target = createUser("promote@test.com", "Do", "Awansu", UserRole.USER);

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/promote")
                .header("Authorization", "Bearer " + superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldRejectPromoteByPlainAdmin() throws Exception {
        User target = createUser("promote2@test.com", "Do", "Awansu", UserRole.USER);

        // testadmin@kdtraining.test is NOT the super-admin from .env → promotion forbidden (409)
        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/promote")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectDemoteByPlainAdmin() throws Exception {
        User target = createUser("admin3@test.com", "Inny", "Admin", UserRole.ADMIN);

        // testadmin@kdtraining.test is NOT the super-admin from .env → demotion forbidden (409)
        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/demote")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldDemoteAdminBySuperAdmin() throws Exception {
        User target = createUser("admin4@test.com", "Do", "Degradacji", UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/demote")
                .header("Authorization", "Bearer " + superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldRejectDemoteOfSuperAdminTarget() throws Exception {
        // a super-admin cannot demote themselves or another super-admin (here: themselves → 409)
        User superAdmin = userRepository.findByEmail("admin@kdtraining.test")
                .orElseGet(() -> createUser("admin@kdtraining.test", "Env", "Admin", UserRole.ADMIN));

        mockMvc.perform(post("/api/admin/users/" + superAdmin.getId() + "/demote")
                .header("Authorization", "Bearer " + superAdminToken()))
            .andExpect(status().isConflict());
    }

    // --- delete ---

    @Test
    void shouldDeleteUser() throws Exception {
        User target = createUser("todelete@test.com", "Do", "Usunięcia", UserRole.USER);

        mockMvc.perform(delete("/api/admin/users/" + target.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findById(target.getId()).isEmpty());
    }

    @Test
    void shouldRejectDeleteOfSuperAdmin() throws Exception {
        User superAdmin = userRepository.findByEmail("admin@kdtraining.test")
                .orElseGet(() -> createUser("admin@kdtraining.test", "Env", "Admin", UserRole.ADMIN));

        mockMvc.perform(delete("/api/admin/users/" + superAdmin.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound());
    }

    // --- send email ---

    @Test
    void shouldSendEmailToSelectedUsers() throws Exception {
        User target = createUser("recipient@test.com", "Adresat", "Testowy", UserRole.USER);

        mockMvc.perform(post("/api/admin/users/email")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subject":"Witaj","message":"Treść wiadomości","audience":"SELECTED","userIds":["%s"]}
                    """.formatted(target.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipientCount").value(1));
    }

    @Test
    void shouldRejectEmailWithNoRecipients() throws Exception {
        mockMvc.perform(post("/api/admin/users/email")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subject":"Witaj","message":"Treść","audience":"SELECTED","userIds":[]}
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectEmailWithBlankSubject() throws Exception {
        mockMvc.perform(post("/api/admin/users/email")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subject":"","message":"Treść","audience":"ALL"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
