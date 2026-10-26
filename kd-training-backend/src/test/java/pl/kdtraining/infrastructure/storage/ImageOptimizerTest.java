package pl.kdtraining.infrastructure.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer();

    @Test
    void shouldSkipSmallImage() throws IOException {
        byte[] imageBytes = createTestImage(800, 600);
        var result = optimizer.optimize(new ByteArrayInputStream(imageBytes), ".jpg");

        assertEquals(".jpg", result.extension());
        assertNotNull(result.inputStream());
    }

    @Test
    void shouldResizeLargeImage() throws IOException {
        byte[] imageBytes = createTestImage(4000, 3000);
        var result = optimizer.optimize(new ByteArrayInputStream(imageBytes), ".jpg");

        assertEquals(".jpg", result.extension());
        BufferedImage output = ImageIO.read(result.inputStream());
        assertNotNull(output);
        assertTrue(output.getWidth() <= 1920);
        assertTrue(output.getHeight() <= 1920);
    }

    @Test
    void shouldPreservePngExtension() throws IOException {
        byte[] imageBytes = createTestImage(3000, 2000, "png");
        var result = optimizer.optimize(new ByteArrayInputStream(imageBytes), ".png");

        assertEquals(".png", result.extension());
    }

    @Test
    void shouldPassThroughNonImageData() throws IOException {
        byte[] garbage = "not an image".getBytes();
        var result = optimizer.optimize(new ByteArrayInputStream(garbage), ".jpg");

        assertEquals(".jpg", result.extension());
        assertNotNull(result.inputStream());
    }

    @Test
    void shouldRejectAPictureTooBigToFitInTheHeap() throws IOException {
        // 25 MPx: over the cap, and deliberately UNDER the 40 MPx this used to allow — so putting the
        // old number back fails here rather than passing quietly. Decoded it would be ~100 MB, on a
        // heap of ~211 MB, from a file of a few hundred bytes.
        byte[] bomb = pngClaiming(5_000, 5_000);

        var e = assertThrows(IllegalArgumentException.class,
                () -> optimizer.optimize(new ByteArrayInputStream(bomb), ".png"));
        assertTrue(e.getMessage().contains("wymiary"));
    }

    @Test
    void shouldStillTakeAFullFrameCameraExport() {
        // The cap has to sit above the photographs the club actually uploads — 6000×4000 is a normal
        // camera export, and the gallery would be the poorer for refusing it. Asserted against the
        // constant rather than by building one: a real 24 MPx image costs ~96 MB to prove a number.
        assertTrue(6_000L * 4_000L <= ImageOptimizer.MAX_PIXELS,
                "a full-frame camera export must stay inside the cap");
    }

    @Test
    void shouldMeasureThePictureFromItsHeaderWithoutDecodingIt() throws IOException {
        // The property the rejection above rests on. If the size were established by decoding, the
        // check would be the very allocation it exists to prevent — this file is 100 bytes and
        // claims 400 MPx, and reading that number must stay free.
        byte[] bomb = pngClaiming(20_000, 20_000);

        assertTrue(bomb.length < 1000);
        assertEquals(400_000_000L, ImageOptimizer.readPixelCount(bomb));
    }

    @Test
    void shouldDecodeOneImageAtATime() throws Exception {
        // The per-image ceiling bounds one request; N requests multiply it. Serialising is what turns
        // the heap budget into a guarantee, so this asserts nobody ever shares the decoder.
        var inFlight = new AtomicInteger();
        var peak = new AtomicInteger();
        var optimizer = new ImageOptimizer() {
            @Override
            protected BufferedImage decode(byte[] bytes) throws IOException {
                peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(60);
                    return super.decode(bytes);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                } finally {
                    inFlight.decrementAndGet();
                }
            }
        };
        byte[] image = createTestImage(800, 600);

        var start = new CountDownLatch(1);
        var done = new CountDownLatch(4);
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    start.await();
                    optimizer.optimize(new ByteArrayInputStream(image), ".jpg");
                    done.countDown();
                    return null;
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "all four uploads should get through");
        }

        assertEquals(1, peak.get(), "two uploads must never hold decoded pixels at the same time");
    }

    /**
     * A one-pixel PNG whose IHDR has been rewritten to claim {@code width × height}, CRC included so
     * the reader accepts the header. The bytes for those pixels do not exist — which is the point:
     * this is what an upload designed to be expensive looks like.
     */
    private static byte[] pngClaiming(int width, int height) throws IOException {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] png = baos.toByteArray();

        // Layout: 8-byte signature, then the IHDR chunk — 4 length, 4 type, then width and height.
        int ihdrData = 8 + 4 + 4;
        ByteBuffer.wrap(png).putInt(ihdrData, width).putInt(ihdrData + 4, height);

        // The reader checks the chunk CRC, computed over the type and the data.
        var crc = new CRC32();
        crc.update(png, ihdrData - 4, 4 + 13);
        ByteBuffer.wrap(png).putInt(ihdrData + 13, (int) crc.getValue());
        return png;
    }

    private byte[] createTestImage(int width, int height) throws IOException {
        return createTestImage(width, height, "jpg");
    }

    private byte[] createTestImage(int width, int height, String format) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}
