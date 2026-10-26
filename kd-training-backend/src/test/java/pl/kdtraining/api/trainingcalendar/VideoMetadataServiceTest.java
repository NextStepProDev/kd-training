package pl.kdtraining.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.VideoMetadataResponse;
import pl.kdtraining.domain.training.ExerciseVideo;
import pl.kdtraining.domain.training.ExerciseVideoRepository;
import pl.kdtraining.domain.training.TrainingAttachmentRepository;
import pl.kdtraining.domain.training.YouTubeMetadata;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.youtube.YouTubeOEmbedClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The lookup behind the video form. Never touches the network: the oEmbed client is stubbed, which
 * is also the point — a test that called YouTube would fail on a train.
 */
class VideoMetadataServiceTest {

    private ExerciseVideoRepository repository;
    private YouTubeOEmbedClient oEmbed;
    private ExerciseVideoService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExerciseVideoRepository.class);
        oEmbed = mock(YouTubeOEmbedClient.class);
        MessageService msg = mock(MessageService.class);
        when(msg.get(anyString())).thenReturn("message");
        when(msg.get(anyString(), any())).thenReturn("message");
        when(repository.findByVideoKey(anyString())).thenReturn(Optional.empty());
        service = new ExerciseVideoService(repository, mock(TrainingAttachmentRepository.class), oEmbed, msg);
    }

    @Test
    void shouldReturnTheTitleForPrefilling() {
        when(oEmbed.fetch("abc12345678")).thenReturn(YouTubeMetadata.ok("Przysiad ze sztangą", "KD Training"));

        VideoMetadataResponse response = service.metadata("https://youtu.be/abc12345678?t=30");

        assertEquals("OK", response.status());
        assertEquals("Przysiad ze sztangą", response.title());
        assertEquals("KD Training", response.authorName());
        assertNull(response.duplicateName());
    }

    @Test
    void shouldFlagAClipThatWillNotEmbed() {
        // The whole reason this lookup exists: a private video saves happily and shows the client an
        // empty player days later.
        when(oEmbed.fetch(anyString())).thenReturn(YouTubeMetadata.unavailable());

        assertEquals("UNAVAILABLE", service.metadata("https://www.youtube.com/watch?v=abc12345678").status());
    }

    @Test
    void shouldStaySilentRatherThanBlockWhenYouTubeDoesNotAnswer() {
        // A slow network is not the user's fault, and the title is a convenience — the form saves.
        when(oEmbed.fetch(anyString())).thenReturn(YouTubeMetadata.unknown());

        VideoMetadataResponse response = service.metadata("https://www.youtube.com/watch?v=abc12345678");

        assertEquals("UNKNOWN", response.status());
        assertNull(response.title());
    }

    @Test
    void shouldNameTheExistingClipWhenTheLinkIsADuplicate() {
        // Matching is on the video id, so a different URL shape for the same film still collides.
        ExerciseVideo existing = new ExerciseVideo("Przysiad", "https://youtu.be/abc12345678",
                "abc12345678", null);
        when(repository.findByVideoKey("abc12345678")).thenReturn(Optional.of(existing));
        when(oEmbed.fetch(anyString())).thenReturn(YouTubeMetadata.ok("Przysiad ze sztangą", null));

        assertEquals("Przysiad",
                service.metadata("https://www.youtube.com/watch?v=abc12345678").duplicateName());
    }

    @Test
    void shouldNeverCallYouTubeForSomethingThatIsNotAYouTubeLink() {
        // The request is built from the parsed id. A link that does not parse must not become an
        // outbound request to whatever was typed.
        assertThrows(IllegalArgumentException.class,
                () -> service.metadata("https://internal.local/admin"));

        verifyNoInteractions(oEmbed);
    }
}
