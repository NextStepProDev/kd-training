package pl.kdtraining.infrastructure.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class ImageOptimizer {
    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    /**
     * Ceiling on the decoded picture, checked BEFORE any pixels are read.
     * <p>
     * Byte size says nothing about decode cost: a heavily compressed 1 MB JPEG can describe a
     * 12000x12000 image, and {@code ImageIO.read} would then allocate roughly 4 bytes per pixel —
     * ~576 MB inside a container capped at 384 MB. One request would be enough to get the backend
     * OOM-killed. The header carries the dimensions, so the size can be settled without decoding.
     * <p>
     * The number is arithmetic, not taste. The heap is 55% of a 384 MB container, so ~211 MB; at
     * 4 bytes a pixel this cap costs ~96 MB for the decoded image, plus the source bytes (≤10 MB)
     * and the encoder's output. That fits with the rest of the application still running — and only
     * because {@link #decodePermits} guarantees it is paid once at a time. The two belong together:
     * the previous 40 MPx allowed ~160 MB per request, so a single upload took most of the heap and
     * two concurrent ones ended the process (the JVM runs with {@code ExitOnOutOfMemoryError}).
     * <p>
     * 24 MPx is 6000×4000 — a full-frame camera export passes, a phone photo passes several times
     * over. Anything above that is not a picture somebody wanted to show, it is a picture somebody
     * wanted us to decode.
     */
    static final long MAX_PIXELS = 24_000_000L;

    /**
     * How many uploads may hold decoded pixels at once, and how long the next one waits its turn.
     * <p>
     * A per-request ceiling bounds nothing on its own — N requests multiply it. Uploads are rare
     * (the limiter allows twelve a minute per address, and a person changes their avatar or attaches
     * a watch screenshot in seconds), so serialising them costs nothing anybody will notice, and it
     * turns the heap budget above from an average into a guarantee.
     * <p>
     * Fair queueing so a steady stream of uploads cannot starve one straggler. The wait is short on
     * purpose: requests waiting here are cheap (their bytes are still in Tomcat's temp file, not on
     * the heap), but a queue that never gives up is its own way of falling over.
     */
    private static final int CONCURRENT_DECODES = 1;
    private static final long DECODE_WAIT_MS = 10_000L;

    private final Semaphore decodePermits = new Semaphore(CONCURRENT_DECODES, true);

    /**
     * How an image is re-encoded on the way in.
     *
     * @param maxDimension  longest side after resizing
     * @param quality       encoder quality, 0..1
     * @param sizeThreshold above this many bytes the image is recompressed even when it fits
     * @param forceReencode always run the encoder, even for a small, correctly sized image. Set for
     *                      uploads where the stored bytes must be the server's own output rather
     *                      than whatever the client sent.
     * @param outputFormat  {@code null} keeps the source format, otherwise forces this one
     */
    public record Profile(int maxDimension, double quality, long sizeThreshold,
                          boolean forceReencode, String outputFormat) {

        /** Catalog artwork: photos to look at, so quality wins over bytes. */
        public static final Profile DEFAULT = new Profile(1920, 0.85, 2 * 1024 * 1024, false, "");

        /**
         * A screenshot from a sports watch. Smaller and harder-compressed than catalog artwork
         * because it is read once and then deleted after 30 days — but not so hard that the numbers
         * on the dial stop being legible, which is the entire point of sending it.
         * <p>
         * {@code forceReencode} is what makes the stored file the server's own JPEG: metadata (EXIF,
         * and the GPS tag inside it) does not survive the encoder, and the dimensions become facts
         * rather than claims.
         */
        public static final Profile TRAINING_PHOTO = new Profile(1280, 0.75, 0, true, "jpg");
    }

    public OptimizedImage optimize(InputStream inputStream, String extension) throws IOException {
        return optimize(inputStream, extension, Profile.DEFAULT);
    }

    /**
     * Runs the body under a decode permit, so the heap arithmetic on {@link #MAX_PIXELS} holds for
     * the process and not merely for one request.
     * <p>
     * The permit is taken before the bytes are read into memory: everything expensive — the source
     * array, the decoded image, the encoder's buffer — lives inside it, and a request still waiting
     * holds nothing but the temp file the servlet container already wrote.
     */
    public OptimizedImage optimize(InputStream inputStream, String extension, Profile profile) throws IOException {
        boolean acquired;
        try {
            acquired = decodePermits.tryAcquire(DECODE_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageDecodeBusyException();
        }
        if (!acquired) {
            log.warn("Upload rejected: no decode permit free after {} ms", DECODE_WAIT_MS);
            throw new ImageDecodeBusyException();
        }
        try {
            return optimizeExclusively(inputStream, extension, profile);
        } finally {
            decodePermits.release();
        }
    }

    private OptimizedImage optimizeExclusively(InputStream inputStream, String extension, Profile profile)
            throws IOException {
        byte[] originalBytes = inputStream.readAllBytes();

        // Dimensions first, from the header alone. Decoding a bomb to find out how big it is would
        // be the very allocation this check exists to prevent.
        long pixels = readPixelCount(originalBytes);
        if (pixels > MAX_PIXELS) {
            log.debug("Rejected an upload declaring {} pixels (max {})", pixels, MAX_PIXELS);
            throw new IllegalArgumentException("Obraz ma zbyt duże wymiary");
        }

        BufferedImage image = decode(originalBytes);

        if (image == null) {
            // Either WebP (the JDK ships no reader, so it passes through untouched) or a damaged file.
            // Which of the two it is, is settled by the signature check in LocalFileStorageService —
            // this class only reports that nothing could be decoded.
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, false, 0, 0);
        }

        boolean needsResize = image.getWidth() > profile.maxDimension() || image.getHeight() > profile.maxDimension();
        boolean needsCompression = originalBytes.length > profile.sizeThreshold();

        if (!needsResize && !needsCompression && !profile.forceReencode()) {
            log.debug("Image already optimized ({}×{}, {} KB) — skipping", image.getWidth(), image.getHeight(), originalBytes.length / 1024);
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, true,
                    image.getWidth(), image.getHeight());
        }

        String outputFormat = profile.outputFormat().isEmpty() ? outputFormat(extension) : profile.outputFormat();
        var baos = new ByteArrayOutputStream();

        var builder = Thumbnails.of(image);
        if (needsResize) {
            builder.size(profile.maxDimension(), profile.maxDimension());
        } else {
            builder.scale(1.0);
        }
        builder.outputQuality(profile.quality())
                .outputFormat(outputFormat)
                .toOutputStream(baos);

        byte[] encoded = baos.toByteArray();
        // Thumbnailator preserves the aspect ratio, so re-reading the header is the only way to learn
        // the exact output size — and the client needs it to reserve the box before the bytes arrive.
        int width = image.getWidth();
        int height = image.getHeight();
        if (needsResize) {
            double ratio = Math.min((double) profile.maxDimension() / width, (double) profile.maxDimension() / height);
            width = Math.max(1, (int) Math.round(width * ratio));
            height = Math.max(1, (int) Math.round(height * ratio));
        }

        log.info("Optimized image: {} KB → {} KB ({}×{} → {}×{}, format: {})",
                originalBytes.length / 1024, encoded.length / 1024,
                image.getWidth(), image.getHeight(), width, height, outputFormat);

        String newExtension = "." + outputFormat;
        return new OptimizedImage(new ByteArrayInputStream(encoded), newExtension, true, width, height);
    }

    /**
     * The one allocation this class exists to bound, behind its own method so a test can watch how
     * many callers are inside it at once. Overridden nowhere in production.
     */
    protected BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /**
     * Width × height straight from the file header, without decoding a single pixel.
     * <p>
     * Package-private rather than private so the test can prove the number comes from the header:
     * a file that claims 400 MPx in twenty bytes must be measured without those pixels ever
     * existing, which is the whole reason the check runs before {@link #decode}.
     *
     * @return 0 when no reader recognises the bytes — WebP takes this path, and so does a damaged
     *         file; both are settled later by the signature check and the decode attempt.
     */
    static long readPixelCount(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return 0;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return (long) reader.getWidth(0) * reader.getHeight(0);
            } catch (IOException | RuntimeException e) {
                // A header too broken to state its own size. Not our call to make here — the decode
                // attempt below turns it into the same 400 as every other damaged upload.
                log.debug("Could not read image dimensions from header: {}", e.getMessage());
                return 0;
            } finally {
                reader.dispose();
            }
        }
    }

    private String outputFormat(String extension) {
        return switch (extension.toLowerCase()) {
            case ".png" -> "png";
            case ".webp" -> "webp";
            default -> "jpg";
        };
    }

    /**
     * @param decoded whether the image could actually be read. False means the bytes never went through a
     *                decoder, so nothing about them has been verified here.
     * @param width   stored dimensions, both 0 when {@code decoded} is false
     */
    public record OptimizedImage(InputStream inputStream, String extension, boolean decoded,
                                 int width, int height) {}
}
