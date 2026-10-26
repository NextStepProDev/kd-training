package pl.kdtraining.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import pl.kdtraining.BaseIntegrationTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryExcludeTest extends BaseIntegrationTest {

    @Test
    void shouldExcludeHiddenEmailsAgainstRealDatabase() {
        userRepository.save(verified(new User("jan@test.com", "Jan", "Kowalski", null)));
        userRepository.save(verified(new User("dev@test.com", "Dev", "Eloper", null)));

        Page<User> page = userRepository.findAllExcludingEmails(
                Set.of("dev@test.com"),
                PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(1, page.getTotalElements());
        assertEquals("jan@test.com", page.getContent().getFirst().getEmail());
    }

    @Test
    void shouldExcludeHiddenEmailsSortedByName() {
        userRepository.save(verified(new User("jan@test.com", "Jan", "Kowalski", null)));
        userRepository.save(verified(new User("dev@test.com", "Dev", "Eloper", null)));

        Page<User> page = userRepository.findAllExcludingEmails(
                Set.of("dev@test.com"),
                PageRequest.of(0, 30, Sort.by(Sort.Direction.ASC, "lastName", "firstName")));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void shouldExcludeHiddenEmailsWhileSearchingAgainstRealDatabase() {
        userRepository.save(verified(new User("jan@test.com", "Jan", "Kowalski", null)));
        userRepository.save(verified(new User("dev@test.com", "Dev", "Kowalski", null)));

        Page<User> page = userRepository.searchByPhraseExcludingEmails(
                "kowal", Set.of("dev@test.com"), PageRequest.of(0, 30));

        assertEquals(1, page.getTotalElements());
        assertEquals("jan@test.com", page.getContent().getFirst().getEmail());
    }

    private static User verified(User u) {
        u.setPasswordHash("$2a$12$dummyhash");
        u.markEmailVerified();
        return u;
    }
}
