package pl.kdtraining.api;

/**
 * Thrown when a refresh token cannot buy a new session: malformed, expired, of the wrong type, or
 * revoked (rotated out, logged out, or wiped by a forced logout). Mapped to HTTP <strong>401</strong>
 * in {@link GlobalExceptionHandler}.
 *
 * <p>It extends {@link IllegalArgumentException} — which the handler maps to 400 — purely so the
 * status can differ without reclassifying the failure or touching callers. The status is the whole
 * point of the class: the frontend ends a session on 401/403 and on nothing else, because a 429 from
 * the rate limiter or a 502 mid-deploy must leave a valid login alone. Answering 400 here put a truly
 * dead refresh token in the same bucket as "the server was busy", and the app would then keep a
 * corpse of a session forever, insisting the user was logged in while every request failed.
 *
 * <p>So: this exception means "these credentials are refused, stop trying". Anything that merely
 * failed to get an answer must NOT use it.
 */
public class InvalidRefreshTokenException extends IllegalArgumentException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
