package pl.kdtraining.infrastructure.storage;

import org.jspecify.annotations.Nullable;

/**
 * The image types the application accepts, recognised by what the bytes actually are.
 * <p>
 * The declared content type and the file extension both come from the client and both are a single
 * header away from saying anything. Decoding used to be the real check — {@code ImageIO.read} either
 * understood the file or it did not — but the JDK ships no WebP reader, so undecodable input is
 * deliberately stored as-is to keep WebP uploads working. That leaves {@code .webp} as a hole with no
 * content check at all: arbitrary bytes, stored under a name the app hands back out.
 * <p>
 * Serving is already hardened — {@link pl.kdtraining.api.file.FileController} forces the content type
 * from the extension and sends {@code X-Content-Type-Options: nosniff}, so a browser will not execute
 * whatever this is — which is why the gap is a storage-integrity problem rather than a stored-XSS one.
 * Signatures close it without needing a decoder.
 */
enum ImageFormat {

    JPEG(new int[]{0xFF, 0xD8, 0xFF}),
    PNG(new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    /** RIFF container: "RIFF" ‹4-byte length› "WEBP" — the length in between is why this is not one run. */
    WEBP(new int[]{0x52, 0x49, 0x46, 0x46}, new int[]{0x57, 0x45, 0x42, 0x50});

    /** Enough for the longest signature plus the WebP offset. */
    static final int HEADER_BYTES = 12;

    private final int[] prefix;
    private final int @Nullable [] atOffsetEight;

    ImageFormat(int[] prefix) {
        this(prefix, null);
    }

    ImageFormat(int[] prefix, int @Nullable [] atOffsetEight) {
        this.prefix = prefix;
        this.atOffsetEight = atOffsetEight;
    }

    /** The format these bytes really are, or {@code null} for anything this app does not accept. */
    @Nullable
    static ImageFormat sniff(byte[] head) {
        for (ImageFormat format : values()) {
            if (format.matches(head)) {
                return format;
            }
        }
        return null;
    }

    private boolean matches(byte[] head) {
        if (!matchesAt(head, 0, prefix)) {
            return false;
        }
        return atOffsetEight == null || matchesAt(head, 8, atOffsetEight);
    }

    private static boolean matchesAt(byte[] head, int offset, int[] expected) {
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** Whether the extension the upload claims describes this format — ".jpeg" and ".jpg" are one. */
    boolean matchesExtension(String extension) {
        return switch (this) {
            case JPEG -> extension.equals(".jpg") || extension.equals(".jpeg");
            case PNG -> extension.equals(".png");
            case WEBP -> extension.equals(".webp");
        };
    }

    /**
     * Whether the JDK can decode this format. WebP cannot be, so it is stored unmodified — which is
     * exactly why its signature has to be checked here instead.
     */
    boolean isDecodable() {
        return this != WEBP;
    }
}
