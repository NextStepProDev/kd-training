package pl.kdtraining.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kdtraining.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PasswordPolicyValidatorTest {

    private PwnedPasswordChecker pwnedChecker;
    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        MessageService msg = mock(MessageService.class);
        // Echo the key back so assertions can identify which rule fired.
        when(msg.get(anyString())).thenAnswer(i -> i.getArgument(0));
        pwnedChecker = mock(PwnedPasswordChecker.class);
        when(pwnedChecker.isPwned(any())).thenReturn(false); // not breached by default
        validator = new PasswordPolicyValidator(msg, pwnedChecker);
    }

    private void validate(String pw) {
        validator.validate(pw, "jan.kowalski@example.com", "Jan", "Kowalski");
    }

    @Test
    void shouldAcceptStrongPassword() {
        assertDoesNotThrow(() -> validate("zielony-Rower-2024"));
    }

    @Test
    void shouldRejectTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validate("Abc123"));
        assertEquals("validation.password.size", ex.getMessage());
    }

    @Test
    void shouldRejectPasswordContainingEmailLocalPart() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validate("kowalski-haslo-9"));
        assertEquals("validation.password.personal", ex.getMessage());
    }

    @Test
    void shouldRejectPasswordContainingLastName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("super-Kowalski-1", "x@example.com", "Jan", "Kowalski"));
        assertEquals("validation.password.personal", ex.getMessage());
    }

    @Test
    void shouldNotOverBlockOnShortName() {
        // First name "Ola" (3 chars) is below the personal-token threshold, so a password that merely
        // contains "ola" is not blocked for that reason.
        assertDoesNotThrow(() -> validator.validate("wesola-okolica-7", "kontakt@example.com", "Ola", "Nowak"));
    }

    @Test
    void shouldRejectBreachedPassword() {
        when(pwnedChecker.isPwned("zielony-Rower-2024")).thenReturn(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validate("zielony-Rower-2024"));
        assertEquals("validation.password.pwned", ex.getMessage());
    }

    @Test
    void shouldCheckLengthAndPersonalBeforeHittingHibp() {
        // Cheap local checks run first; HIBP is not consulted for an obviously invalid password.
        assertThrows(IllegalArgumentException.class, () -> validate("Abc123"));
        verify(pwnedChecker, never()).isPwned(any());
    }
}
