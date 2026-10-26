package pl.kdtraining.domain.training;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeUrlTest {

    private static final String ID = "dQw4w9WgXcQ";

    @Test
    void shouldParseEveryShapePeopleActuallyPaste() {
        assertEquals(ID, YouTubeUrl.parse("https://www.youtube.com/watch?v=" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://youtube.com/watch?v=" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://m.youtube.com/watch?v=" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://youtu.be/" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://www.youtube.com/embed/" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://www.youtube.com/shorts/" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://www.youtube.com/live/" + ID).orElseThrow().key());
    }

    @Test
    void shouldIgnoreTrailingQueryNoise() {
        // Copying from the app appends a timestamp; copying from a playlist appends the list id.
        assertEquals(ID, YouTubeUrl.parse("https://youtu.be/" + ID + "?t=30").orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://www.youtube.com/watch?v=" + ID + "&list=PL123").orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("https://www.youtube.com/watch?list=PL123&v=" + ID).orElseThrow().key());
        assertEquals(ID, YouTubeUrl.parse("  https://youtu.be/" + ID + "  ").orElseThrow().key());
    }

    @Test
    void shouldTreatTheSameVideoInDifferentShapesAsOne() {
        // This is what makes duplicate detection work: the library keys on the id, not the URL.
        String fromShare = YouTubeUrl.parse("https://youtu.be/" + ID + "?t=12").orElseThrow().key();
        String fromAddressBar = YouTubeUrl.parse("https://www.youtube.com/watch?v=" + ID).orElseThrow().key();
        assertEquals(fromShare, fromAddressBar);
    }

    @Test
    void shouldRejectAnythingThatIsNotYouTube() {
        assertTrue(YouTubeUrl.parse("https://vimeo.com/123456").isEmpty());
        assertTrue(YouTubeUrl.parse("https://example.com/watch?v=" + ID).isEmpty());
        // A lookalike host must not slip through
        assertTrue(YouTubeUrl.parse("https://youtube.com.evil.example/watch?v=" + ID).isEmpty());
        assertTrue(YouTubeUrl.parse("nonsense").isEmpty());
        assertTrue(YouTubeUrl.parse("").isEmpty());
        assertTrue(YouTubeUrl.parse(null).isEmpty());
    }

    @Test
    void shouldRejectIdsOfTheWrongLength() {
        assertTrue(YouTubeUrl.parse("https://youtu.be/short").isEmpty());
        assertFalse(YouTubeUrl.isValidKey("short"));
        assertFalse(YouTubeUrl.isValidKey(ID + "extra"));
        assertTrue(YouTubeUrl.isValidKey(ID));
    }

    @Test
    void shouldBuildPlayerUrlsFromTheIdRatherThanThePastedLink() {
        // A messy pasted URL must never reach an iframe src.
        YouTubeUrl video = YouTubeUrl.parse("https://www.youtube.com/watch?v=" + ID + "&list=PL1").orElseThrow();
        assertEquals("https://www.youtube-nocookie.com/embed/" + ID, video.embedUrl());
        assertEquals("https://img.youtube.com/vi/" + ID + "/hqdefault.jpg", video.thumbnailUrl());
    }
}
