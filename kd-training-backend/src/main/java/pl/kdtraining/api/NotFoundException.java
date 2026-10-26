package pl.kdtraining.api;

/**
 * Thrown when the requested resource does not exist (or is inactive/hidden for the requester).
 * Mapped to HTTP 404 in {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
