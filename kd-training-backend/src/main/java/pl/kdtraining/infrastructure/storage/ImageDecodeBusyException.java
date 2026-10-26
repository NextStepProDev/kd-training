package pl.kdtraining.infrastructure.storage;

/**
 * Every decode permit was taken and this upload waited long enough.
 * <p>
 * Answered as <strong>429</strong>, not 503: what happened is that too many uploads arrived at once,
 * which is the same sentence the rate limiter says, and the frontend already renders it as "too many
 * requests, try again in a moment". A 503 would be read by the client as a gateway error and shown
 * as "the service is being updated" — a wrong and worrying thing to tell somebody whose real problem
 * is that they should press the button again in five seconds.
 * <p>
 * Extends {@link IllegalStateException} so that callers, and the default 409 mapping, keep working
 * unchanged if this ever escapes by a path the handler does not cover — the same arrangement as
 * {@code InvalidRefreshTokenException}.
 */
public class ImageDecodeBusyException extends IllegalStateException {

    public ImageDecodeBusyException() {
        super("Serwer przetwarza teraz inne zdjęcie. Spróbuj ponownie za chwilę.");
    }
}
