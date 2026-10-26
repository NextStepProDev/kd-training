package pl.kdtraining.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.admin.AdminUserDtos.*;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.mail.AdminUserMailService;
import pl.kdtraining.infrastructure.security.JwtAuthenticationFilter;
import pl.kdtraining.infrastructure.storage.FileStorageService;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private AdminUserMailService adminUserMailService;
    @Mock private FileStorageService fileStorageService;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private MessageService msg;
    @Mock private pl.kdtraining.api.trainingcalendar.TrainingPhotoService trainingPhotoService;

    @InjectMocks private AdminUserService service;

    private User regular;
    private UUID regularId;

    @BeforeEach
    void setUp() throws Exception {
        when(msg.get(anyString())).thenReturn("Komunikat");
        when(adminEmailConfig.isAdminEmail(anyString())).thenReturn(false);

        regularId = UUID.randomUUID();
        regular = user(regularId, "jan@test.com", "Jan", "Kowalski", UserRole.USER);
    }

    // --- list / search ---

    @Test
    void shouldListAllUsersWhenNoSearch() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list(null, 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        assertEquals("jan@test.com", result.content().getFirst().email());
        verify(userRepository).findAll(any(Pageable.class));
        verify(userRepository, never()).searchByPhrase(any(), any());
    }

    private static final java.util.Set<String> ALLOWED_SORT_PROPERTIES =
            java.util.Set.of("lastName", "firstName", "email", "role", "marketingConsentAt", "createdAt", "id");

    @ParameterizedTest(name = "sort=\"{0}\" dir={1} -> {3} {2}")
    @CsvSource({
            "name,       asc,  lastName,           ASC",
            "name,       desc, lastName,           DESC",
            "email,      asc,  email,              ASC",
            "role,       desc, role,               DESC",
            "marketing,  asc,  marketingConsentAt, ASC",
            "created,    desc, createdAt,          DESC",
            "phone,      asc,  createdAt,          ASC",   // phone is not sortable -> default
            "garbage,    desc, createdAt,          DESC",  // value outside the whitelist -> default
            "'',         asc,  createdAt,          ASC"    // empty -> default
    })
    void shouldResolveSortFromWhitelistOnly(String sort, String direction,
                                            String expectedProperty, Sort.Direction expectedDir) {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, sort, direction);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor(expectedProperty);
        assertNotNull(order, "Brak sortowania po " + expectedProperty);
        assertEquals(expectedDir, order.getDirection());
        // The whitelist must not let an arbitrary property through (protection against injection into Sort).
        captor.getValue().getSort().forEach(o ->
                assertTrue(ALLOWED_SORT_PROPERTIES.contains(o.getProperty()),
                        "Niedozwolona właściwość sortowania: " + o.getProperty()));
    }

    /**
     * Every sort key has to end in something unique, or paging tears.
     * <p>
     * The list is paged 50 at a time and three of the four keys are heavily tied — `role` holds two
     * values and `marketingConsentAt` is null on most accounts. Postgres makes no promise about the
     * order of rows inside a tie, and it does not have to make the same choice for OFFSET 0 as for
     * OFFSET 50: the same person can then appear on both pages while somebody else appears on
     * neither. Asserted on the Sort rather than against a live database, because a database test for
     * this passes or fails by luck.
     */
    @ParameterizedTest(name = "sort=\"{0}\" ends with id")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"name", "email", "role", "marketing", "created", "garbage"})
    void shouldBreakEverySortTieOnId(String sort) {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 50, sort, "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        List<String> properties = captor.getValue().getSort().toList().stream()
                .map(Sort.Order::getProperty).toList();
        assertEquals("id", properties.getLast(),
                "Sortowanie po \"" + sort + "\" nie kończy się unikalną kolumną: " + properties);
    }

    @Test
    void shouldSortByLastNameThenFirstNameForNameKey() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, "name", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertEquals(List.of("lastName", "firstName", "id"),
                captor.getValue().getSort().toList().stream().map(Sort.Order::getProperty).toList());
    }

    @Test
    void shouldSearchUsersByPhraseTrimmed() {
        when(userRepository.searchByPhrase(eq("kowal"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list("  kowal  ", 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        verify(userRepository).searchByPhrase(eq("kowal"), any(Pageable.class));
    }

    @Test
    void shouldExcludeHiddenEmailsWhenListingWithoutSearch() {
        when(adminEmailConfig.getHiddenEmails()).thenReturn(java.util.Set.of("dev@test.com"));
        when(userRepository.findAllExcludingEmails(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list(null, 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        verify(userRepository).findAllExcludingEmails(eq(java.util.Set.of("dev@test.com")), any(Pageable.class));
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldExcludeHiddenEmailsWhenSearching() {
        when(adminEmailConfig.getHiddenEmails()).thenReturn(java.util.Set.of("dev@test.com"));
        when(userRepository.searchByPhraseExcludingEmails(eq("kowal"), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list("  kowal  ", 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        verify(userRepository).searchByPhraseExcludingEmails(eq("kowal"), anyCollection(), any(Pageable.class));
        verify(userRepository, never()).searchByPhrase(any(), any());
    }

    @Test
    void shouldMarkSuperAdminInResponse() {
        when(adminEmailConfig.isAdminEmail("jan@test.com")).thenReturn(true);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        AdminUserResponse response = service.list(null, 0, 30, "created", "desc").content().getFirst();

        assertTrue(response.superAdmin());
    }

    @Test
    void shouldReturnPaginationMetadata() {
        // 70 total, page 1, size 30 → 3 pages
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular), PageRequest.of(1, 30), 70));

        PagedUsersResponse result = service.list(null, 1, 30, "created", "desc");

        assertEquals(1, result.page());
        assertEquals(30, result.size());
        assertEquals(70, result.totalElements());
        assertEquals(3, result.totalPages());
    }

    @Test
    void shouldClampPageSizeToMaximum() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 5000, "created", "desc");

        // page size clamped to MAX_PAGE_SIZE (100)
        verify(userRepository).findAll(argThat((Pageable p) -> p.getPageSize() == 100));
    }

    @Test
    void shouldSortByNameAscending() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, "name", "asc");

        // „name" → sort by lastName (then firstName), direction ASC
        verify(userRepository).findAll(argThat((Pageable p) -> {
            var order = p.getSort().getOrderFor("lastName");
            return order != null && order.isAscending();
        }));
    }

    @Test
    void shouldDefaultToCreatedDescForUnknownSortField() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, "phone", "asc");

        // field outside the whitelist (e.g. phone) → defaults to createdAt
        verify(userRepository).findAll(argThat((Pageable p) ->
                p.getSort().getOrderFor("createdAt") != null
                        && p.getSort().getOrderFor("lastName") == null));
    }

    // --- detail ---

    @Test
    void shouldReturnUserDetail() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        AdminUserDetailResponse detail = service.getDetail(regularId);

        assertEquals("jan@test.com", detail.email());
    }

    @Test
    void shouldThrowWhenUserDetailNotFound() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getDetail(missing));
    }

    // --- send email ---

    @Test
    void shouldSendEmailToAllUsers() {
        User other = user(UUID.randomUUID(), "anna@test.com", "Anna", "Nowak", UserRole.USER);
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(regular, other));

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "ALL", null));

        assertEquals(2, result.recipientCount());
        List<AdminUserMailService.CampaignRecipient> sent = capturedCampaign("Temat", "Treść");
        assertEquals(2, sent.size());
        assertEquals(new AdminUserMailService.CampaignRecipient("jan@test.com", "Jan", null), sent.get(0));
        assertEquals(new AdminUserMailService.CampaignRecipient("anna@test.com", "Anna", null), sent.get(1));
    }

    /** Verifies a single sendCampaign dispatch (one background task, not one @Async per recipient) and returns its recipients. */
    private List<AdminUserMailService.CampaignRecipient> capturedCampaign(String subject, String message) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminUserMailService.CampaignRecipient>> captor =
                ArgumentCaptor.forClass((Class<List<AdminUserMailService.CampaignRecipient>>) (Class<?>) List.class);
        verify(adminUserMailService).sendCampaign(captor.capture(), eq(subject), eq(message));
        return captor.getValue();
    }

    @Test
    void shouldSkipHiddenEmailRecipientsWhenEmailingAll() {
        User dev = user(UUID.randomUUID(), "dev@test.com", "Dev", "Eloper", UserRole.ADMIN);
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(regular, dev));
        when(adminEmailConfig.isHiddenEmail("dev@test.com")).thenReturn(true);

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "ALL", null));

        assertEquals(1, result.recipientCount());
        List<AdminUserMailService.CampaignRecipient> sent = capturedCampaign("Temat", "Treść");
        assertEquals(List.of(new AdminUserMailService.CampaignRecipient("jan@test.com", "Jan", null)), sent);
    }

    @Test
    void shouldSendEmailToSelectedUsers() {
        when(userRepository.findAllById(List.of(regularId))).thenReturn(List.of(regular));

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "SELECTED", List.of(regularId)));

        assertEquals(1, result.recipientCount());
        verify(userRepository, never()).findAllByOrderByCreatedAtDesc();
        List<AdminUserMailService.CampaignRecipient> sent = capturedCampaign("Temat", "Treść");
        assertEquals(List.of(new AdminUserMailService.CampaignRecipient("jan@test.com", "Jan", null)), sent);
    }

    @Test
    void shouldThrowWhenNoRecipientsSelected() {
        assertThrows(IllegalStateException.class, () -> service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "SELECTED", List.of())));
        verifyNoInteractions(adminUserMailService);
    }

    @Test
    void shouldSendMarketingEmailOnlyToConsentersWithUnsubscribeLink() {
        when(userRepository.findAllByMarketingConsentAtIsNotNullOrderByCreatedAtDesc())
                .thenReturn(List.of(regular));

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Nowy obóz!", "Zapraszamy", "MARKETING", null));

        assertEquals(1, result.recipientCount());
        verify(userRepository).findAllByMarketingConsentAtIsNotNullOrderByCreatedAtDesc();
        verify(userRepository, never()).findAllByOrderByCreatedAtDesc();
        // A marketing mail gets an unsubscribe token (non-null); a service mail got null.
        List<AdminUserMailService.CampaignRecipient> sent = capturedCampaign("Nowy obóz!", "Zapraszamy");
        assertEquals(1, sent.size());
        assertEquals("jan@test.com", sent.getFirst().email());
        assertNotNull(sent.getFirst().unsubscribeToken());
    }

    @Test
    void shouldThrowWhenAudienceInvalid() {
        assertThrows(IllegalArgumentException.class, () -> service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "WHATEVER", null)));
        verifyNoInteractions(adminUserMailService);
    }

    // --- delete ---

    @Test
    void shouldDeleteUserWithFilesTokensAndCache() {
        UUID adminId = UUID.randomUUID();
        regular.setAvatarFilename("avatar.jpg");

        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        service.delete(adminId, regularId, true);

        // Photos first: the comment rows naming the files go through the DB cascade with the account.
        var order = inOrder(trainingPhotoService, userRepository);
        order.verify(trainingPhotoService).purgeForUser(regularId);
        order.verify(userRepository).delete(regular);
        verify(fileStorageService).delete("avatars", "avatar.jpg");
        verify(authTokenRepository).deleteAllByUserId(regularId);
        verify(jwtAuthenticationFilter).evictUser(regularId);
    }

    @Test
    void shouldNotifyUserWhenDeletingWithNotify() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        service.delete(UUID.randomUUID(), regularId, true);

        verify(adminUserMailService).sendAccountDeletedNotification(regular.getEmail(), regular.getFirstName());
    }

    @Test
    void shouldNotNotifyUserWhenNotifyFalse() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        service.delete(UUID.randomUUID(), regularId, false);

        verify(adminUserMailService, never()).sendAccountDeletedNotification(any(), any());
    }

    @Test
    void shouldThrowWhenDeletingSelf() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        assertThrows(IllegalStateException.class, () -> service.delete(regularId, regularId, true));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenDeletingSuperAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThrows(IllegalStateException.class, () -> service.delete(UUID.randomUUID(), superAdmin.getId(), true));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentUser() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.delete(UUID.randomUUID(), missing, true));
    }

    // --- promote ---

    @Test
    void shouldPromoteUserToAdminWhenCallerIsSuperAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        AdminUserResponse result = service.promote(superAdmin.getId(), regularId);

        assertEquals(UserRole.ADMIN.name(), result.role());
        assertEquals(UserRole.ADMIN, regular.getRole());
        verify(userRepository).save(regular);
        verify(jwtAuthenticationFilter).evictUser(regularId);
    }

    @Test
    void shouldThrowWhenPromoteCallerIsNotSuperAdmin() {
        User plainAdmin = user(UUID.randomUUID(), "boss@test.com", "Boss", "Admin", UserRole.ADMIN);
        when(userRepository.findById(plainAdmin.getId())).thenReturn(Optional.of(plainAdmin));

        assertThrows(IllegalStateException.class,
                () -> service.promote(plainAdmin.getId(), regularId));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPromotingExistingAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        User admin = user(UUID.randomUUID(), "boss@test.com", "Boss", "Admin", UserRole.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(IllegalStateException.class, () -> service.promote(superAdmin.getId(), admin.getId()));
        verify(userRepository, never()).save(any());
    }

    // --- demote ---

    @Test
    void shouldDemoteAdminWhenCallerIsSuperAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        User targetAdmin = user(UUID.randomUUID(), "boss@test.com", "Boss", "Admin", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));

        AdminUserResponse result = service.demote(superAdmin.getId(), targetAdmin.getId());

        assertEquals(UserRole.USER.name(), result.role());
        assertEquals(UserRole.USER, targetAdmin.getRole());
        verify(jwtAuthenticationFilter).evictUser(targetAdmin.getId());
    }

    @Test
    void shouldThrowWhenDemoteCallerIsNotSuperAdmin() {
        User plainAdmin = user(UUID.randomUUID(), "admin2@test.com", "Zwykły", "Admin", UserRole.ADMIN);
        when(userRepository.findById(plainAdmin.getId())).thenReturn(Optional.of(plainAdmin));

        assertThrows(IllegalStateException.class,
                () -> service.demote(plainAdmin.getId(), UUID.randomUUID()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingSuperAdminTarget() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        User envTarget = user(UUID.randomUUID(), "admin2@kdtraining.test", "Inny", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(adminEmailConfig.isAdminEmail("admin2@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(envTarget.getId())).thenReturn(Optional.of(envTarget));

        assertThrows(IllegalStateException.class,
                () -> service.demote(superAdmin.getId(), envTarget.getId()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingSelf() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThrows(IllegalStateException.class,
                () -> service.demote(superAdmin.getId(), superAdmin.getId()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingNonAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@kdtraining.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@kdtraining.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        assertThrows(IllegalStateException.class,
                () -> service.demote(superAdmin.getId(), regularId));
        verify(userRepository, never()).save(any());
    }

    private static User user(UUID id, String email, String firstName, String lastName, UserRole role) {
        User user = new User(email, firstName, lastName, null);
        user.setRole(role);
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
