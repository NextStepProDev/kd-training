package pl.kdtraining.api;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.storage.ImageDecodeBusyException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageService msg;

    public GlobalExceptionHandler(MessageService msg) {
        this.msg = msg;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    /**
     * A refused refresh token is 401, not the 400 its supertype would get — the frontend ends a
     * session on 401/403 and on nothing else, so a dead token answered with 400 left the app
     * convinced the user was still logged in while nothing worked. Declared above the
     * IllegalArgumentException handler for readability only; Spring picks the closest match by class
     * hierarchy, not by order.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", e.getMessage());
    }

    /**
     * A refusal by method security is 403, not the 500 the catch-all below would have given it.
     *
     * <p>{@code @PreAuthorize} throws from inside the controller, so the exception surfaces in the
     * DispatcherServlet and this advice claims it before it can travel back out to Spring Security's
     * {@code ExceptionTranslationFilter} and the {@code accessDeniedHandler} in {@code SecurityConfig}.
     * Without this method the caller is told the server broke, and every ordinary "you may not do
     * that" is written to the log as {@code Unexpected error} with a full stack trace — on a 1 GB box
     * that is noise standing in front of real failures, and a 5xx that a monitor reads as an outage.
     *
     * <p>Nothing hits this today: the only {@code @PreAuthorize} in {@code main/} sits under
     * {@code /api/admin/**}, which the filter chain already closes a level up, so a stranger is
     * turned away with a proper 403 long before any controller runs. It is the next annotation
     * placed outside that prefix that would find the gap — which is exactly why it is worth closing
     * while nobody is standing in it.
     *
     * <p>Declared on the supertype so it also covers {@code AuthorizationDeniedException}, which is
     * what Spring Security actually throws; the handler is chosen by class hierarchy rather than by
     * the order these methods appear in. The body is the localized message, never
     * {@code e.getMessage()} — that reads "Access Denied", in English, from our internals.
     *
     * <p>403 rather than 401 is right because every request that reaches a controller is already
     * authenticated ({@code anyRequest().authenticated()}). An anonymous caller never gets this far.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        // Debug, not error: being refused is a normal outcome, and logging it as a failure is half
        // of what this method exists to stop.
        log.debug("Access denied: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", msg.get("error.forbidden"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    /**
     * Uploads queue for the decoder, and this one waited too long — 429 rather than the 409 its
     * supertype would get. It is the same event the rate limiter reports (too much at once), it
     * carries the same {@code Retry-After}, and the frontend already turns that status into "try
     * again in a moment" instead of the gateway-error wording it shows for 5xx.
     */
    @ExceptionHandler(ImageDecodeBusyException.class)
    public ResponseEntity<Map<String, Object>> handleImageDecodeBusy(ImageDecodeBusyException e) {
        log.warn("Upload turned away: the image decoder is saturated");
        // Built through error() like every other response here, then re-issued with the header —
        // the body shape stays in one place.
        var base = error(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", e.getMessage());
        return ResponseEntity.status(base.getStatusCode())
            .header("Retry-After", "5")
            .body(base.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errors);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequestParam(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException e) {
        // Malformed/incomplete JSON body (e.g. missing required primitive field, wrong type).
        // e.getMessage() can be verbose and leak internal details — return a generic message.
        log.warn("Malformed request body: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg.get("error.request.body.invalid"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", msg.get("error.method.not.allowed"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", msg.get("error.resource.not.found"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return error(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", msg.get("file.too.large"));
    }

    /**
     * A unique index refused the write — 409, not the 500 the catch-all would give it.
     *
     * <p>Two call sites already catch this locally and turn it into a sentence somebody can act on:
     * registration ("that e-mail is taken") and a second active goal on the same horizon. Those
     * stay, because a specific message beats a generic one. What was missing was a floor under the
     * rest — every other unique index in the schema (an attachment position, a private note per
     * training) had nothing but the catch-all beneath it, so
     * losing a race meant "the server broke" plus a stack trace in the log of a 1 GB box.
     *
     * <p>The message is deliberately generic and localized, never {@code e.getMessage()}: that string
     * carries the constraint name, the table and often the offending values straight from Postgres.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        // Warn rather than error: a lost race is a normal outcome under concurrency, not a fault.
        log.warn("Write refused by a database constraint: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "CONFLICT", msg.get("error.concurrent.modification"));
    }

    /**
     * Two writers, one row, and the second one lost — 409, and specifically not a 500.
     *
     * <p>{@code PersonalTraining} is the only entity carrying {@code @Version}, and
     * {@code PersonalTrainingService} already translates the failure for its own saves. Hibernate can
     * also raise this from a flush the service never wrapped, and any entity given a version later
     * would arrive here with nothing under it. "Somebody else changed this, reload" is an answer;
     * "internal server error" is not.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("Write lost an optimistic lock: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "CONFLICT", msg.get("error.concurrent.modification"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    /**
     * The one place an error body is built — and the reason it is not {@code Map.of}.
     * <p>
     * {@code Map.of} rejects a null value, so an exception carrying no message (a bare
     * {@code new IllegalStateException()}, or one thrown from a library that did not bother) blew up
     * the handler itself. What reached the client was then a container-generated 500 with none of
     * the {@code code}/{@code message}/{@code timestamp} shape the frontend reads — a missing
     * sentence turning a 400 into an unexplained server error.
     */
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, @Nullable String message) {
        String body = message == null || message.isBlank() ? msg.get("error.unexpected") : message;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", body);
        payload.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(payload);
    }
}
