package pl.kdtraining.infrastructure.storage;

import java.util.Set;

/**
 * What an upload has to satisfy to be stored, and how it is re-encoded once it does.
 * <p>
 * Pulled out as a parameter rather than forked into a second upload path on purpose: the checks in
 * {@link LocalFileStorageService#storeImage} — declared content type, extension, byte signature,
 * signature-versus-extension agreement, decodability — are the whole security story of uploads in
 * this service. A parallel implementation for training photos would be a second place to keep them
 * correct, and the copy that fell behind would be the one nobody was looking at.
 *
 * @param maxBytes hard ceiling on the incoming file, checked before anything is read into memory
 */
public record StorePolicy(Set<String> allowedExtensions, Set<String> allowedContentTypes,
                          long maxBytes, ImageOptimizer.Profile profile) {

    /** Profile pictures (avatars). */
    public static final StorePolicy DEFAULT = new StorePolicy(
            Set.of(".jpg", ".jpeg", ".png", ".webp"),
            Set.of("image/jpeg", "image/png", "image/webp"),
            10 * 1024 * 1024,
            ImageOptimizer.Profile.DEFAULT);

    /**
     * Photos attached to 1-on-1 training comments. JPEG only, and that is the point.
     * <p>
     * The JDK ships no WebP reader, so a WebP upload reaches the disk undecoded — its signature is
     * the only thing ever checked about it. That is an acceptable trade for a gym photo and not for
     * health data: JPEG the server can decode and re-encode itself, which is what makes the stored
     * dimensions real rather than claimed, strips EXIF (GPS included), and guarantees that the one
     * content type it later serves is the one the bytes actually are.
     * <p>
     * The browser converts whatever the phone produced — PNG, WebP, HEIC — through a canvas before
     * sending, so the client still picks any screenshot it likes. 1.5 MB is generous against the
     * ~110 KB that conversion actually produces; it exists to stop an upload that skipped it.
     */
    public static final StorePolicy TRAINING_PHOTO = new StorePolicy(
            Set.of(".jpg", ".jpeg"),
            Set.of("image/jpeg"),
            (long) (1.5 * 1024 * 1024),
            ImageOptimizer.Profile.TRAINING_PHOTO);
}
