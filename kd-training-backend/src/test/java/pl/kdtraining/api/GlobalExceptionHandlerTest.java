package pl.kdtraining.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final MessageService msg = mock(MessageService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(msg);

    @Test
    void shouldReturn413WithLocalizedMessageWhenUploadSizeExceeded() {
        when(msg.get("file.too.large")).thenReturn("Plik jest za duży. Maksymalny rozmiar to 10 MB.");
        var ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        var response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("PAYLOAD_TOO_LARGE", body.get("code"));
        assertEquals("Plik jest za duży. Maksymalny rozmiar to 10 MB.", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturn413WhenUploadSizeExceededWithCause() {
        when(msg.get("file.too.large")).thenReturn("Plik jest za duży. Maksymalny rozmiar to 10 MB.");
        var cause = new RuntimeException("size exceeds configured maximum");
        var ex = new MaxUploadSizeExceededException(50 * 1024 * 1024, cause);

        var response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("PAYLOAD_TOO_LARGE", body.get("code"));
    }

    @Test
    void shouldReturn400ForIllegalArgument() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.get("code"));
        assertEquals("bad input", body.get("message"));
    }

    @Test
    void shouldReturn409ForIllegalState() {
        var response = handler.handleIllegalState(new IllegalStateException("conflict"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("CONFLICT", body.get("code"));
    }

    @Test
    void shouldReturn400WhenRequiredParameterMissing() {
        var ex = new MissingServletRequestParameterException("category", "EventCategory");

        var response = handler.handleBadRequestParam(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.get("code"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturn400WhenParameterTypeMismatch() {
        var ex = new MethodArgumentTypeMismatchException("FOO", EventCategoryStub.class, "category", mock(MethodParameter.class), new IllegalArgumentException("bad enum"));

        var response = handler.handleBadRequestParam(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.get("code"));
    }

    private enum EventCategoryStub { CAMP }

    @Test
    void shouldReturn400WhenRequestBodyUnreadable() {
        when(msg.get("error.request.body.invalid")).thenReturn("Nieprawidłowe dane żądania");
        var ex = new HttpMessageNotReadableException("Missing required creator property", mock(HttpInputMessage.class));

        var response = handler.handleUnreadableBody(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.get("code"));
        assertEquals("Nieprawidłowe dane żądania", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturn405WhenHttpMethodNotSupported() {
        when(msg.get("error.method.not.allowed")).thenReturn("Ta metoda nie jest obsługiwana dla tego zasobu");
        var ex = new HttpRequestMethodNotSupportedException("POST");

        var response = handler.handleMethodNotSupported(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("METHOD_NOT_ALLOWED", body.get("code"));
        assertEquals("Ta metoda nie jest obsługiwana dla tego zasobu", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturn404WhenNoResourceFound() {
        when(msg.get("error.resource.not.found")).thenReturn("Nie znaleziono zasobu");
        var ex = new NoResourceFoundException(HttpMethod.GET, "/api/public/nonexistent", "api/public/nonexistent");

        var response = handler.handleNoResource(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("NOT_FOUND", body.get("code"));
        assertEquals("Nie znaleziono zasobu", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturn500ForUnexpectedException() {
        var response = handler.handleGeneral(new RuntimeException("unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("code"));
    }

    /**
     * A unique index refusing a write is a conflict somebody can act on, not a server fault. Three
     * services catch this locally and say something specific; every other unique index in the schema
     * had only the catch-all beneath it, so a lost race answered 500 and wrote a stack trace.
     */
    @Test
    void shouldAnswerConflictWhenADatabaseConstraintRefusesTheWrite() {
        when(msg.get("error.concurrent.modification")).thenReturn("Konflikt zmian");

        var response = handler.handleDataIntegrity(
            new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_enrollments_user_event\""));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("CONFLICT", body.get("code"));
        // Never the driver's message: it carries the constraint name, the table and often the values.
        assertEquals("Konflikt zmian", body.get("message"));
    }

    @Test
    void shouldAnswerConflictWhenAWriteLosesItsOptimisticLock() {
        when(msg.get("error.concurrent.modification")).thenReturn("Konflikt zmian");

        var response = handler.handleOptimisticLock(
            new org.springframework.dao.OptimisticLockingFailureException("row was updated"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("Konflikt zmian", body.get("message"));
    }
}
