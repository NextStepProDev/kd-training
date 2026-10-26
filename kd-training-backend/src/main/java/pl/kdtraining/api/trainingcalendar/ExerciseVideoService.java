package pl.kdtraining.api.trainingcalendar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.Strings;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.kdtraining.domain.training.ExerciseVideo;
import pl.kdtraining.domain.training.ExerciseVideoRepository;
import pl.kdtraining.domain.training.TrainingAttachmentRepository;
import pl.kdtraining.domain.training.YouTubeMetadata;
import pl.kdtraining.domain.training.YouTubeUrl;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.youtube.YouTubeOEmbedClient;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The coach's library of exercise clips.
 * <p>
 * Retiring a clip is {@link #archive} rather than delete: a video referenced by a training cannot be
 * removed (the FK is RESTRICT), and it should not be — the client's history would lose the
 * demonstration that went with a session they already did.
 */
@Service
public class ExerciseVideoService {

    /** Enough to choose from while typing, few enough to scan without scrolling. */
    private static final int SUGGEST_LIMIT = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExerciseVideoRepository repository;
    private final TrainingAttachmentRepository attachmentRepository;
    private final YouTubeOEmbedClient oEmbed;
    private final MessageService msg;

    public ExerciseVideoService(ExerciseVideoRepository repository,
                                TrainingAttachmentRepository attachmentRepository,
                                YouTubeOEmbedClient oEmbed,
                                MessageService msg) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.oEmbed = oEmbed;
        this.msg = msg;
    }

    /**
     * What YouTube knows about a pasted link, so the form can fill the name in and — more usefully —
     * say up front that a clip will not embed. A private video looks fine at save time and turns
     * into an empty player in front of a client days later.
     * <p>
     * Also answers whether the clip is already in the library: pasting a link that is a duplicate is
     * worth knowing before typing a name for it.
     */
    /**
     * Deliberately NOT {@code @Transactional}: YouTube is allowed four seconds to answer, and a
     * connection from the pool held open across a network round trip is a connection nobody else
     * can have. The single lookup below manages its own, for the microseconds it needs.
     */
    public VideoMetadataResponse metadata(String url) {
        YouTubeUrl parsed = parseOrThrow(url);
        YouTubeMetadata found = oEmbed.fetch(parsed.key());
        String duplicateName = repository.findByVideoKey(parsed.key())
                .map(ExerciseVideo::getName)
                .orElse(null);
        return new VideoMetadataResponse(found.status().name(), found.title(), found.authorName(),
                parsed.thumbnailUrl(), duplicateName);
    }

    @Transactional(readOnly = true)
    public PagedExerciseVideos list(String query, boolean includeArchived, int page, int size) {
        Page<ExerciseVideo> result = repository.search(
                normalizeQuery(query), includeArchived,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        return new PagedExerciseVideos(
                result.getContent().stream().map(ExerciseVideoService::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Typeahead while attaching a clip to a training. Never offers archived entries. */
    @Transactional(readOnly = true)
    public List<ExerciseVideoResponse> suggest(String query) {
        return repository.suggest(normalizeQuery(query), PageRequest.of(0, SUGGEST_LIMIT)).stream()
                .map(ExerciseVideoService::toResponse)
                .toList();
    }

    @Transactional
    public ExerciseVideoResponse create(ExerciseVideoRequest request) {
        YouTubeUrl parsed = parseOrThrow(request.url());
        // Keyed on the video id, so the same clip pasted as youtu.be/X and watch?v=X is caught.
        repository.findByVideoKey(parsed.key()).ifPresent(existing -> {
            throw new IllegalStateException(msg.get("exercisevideo.duplicate", existing.getName()));
        });
        ExerciseVideo video = new ExerciseVideo(request.name().trim(), request.url().trim(), parsed.key(),
                Strings.trimToNull(request.description()));
        return toResponse(repository.save(video));
    }

    @Transactional
    public ExerciseVideoResponse update(UUID id, ExerciseVideoRequest request) {
        ExerciseVideo video = require(id);
        YouTubeUrl parsed = parseOrThrow(request.url());
        repository.findByVideoKey(parsed.key()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new IllegalStateException(msg.get("exercisevideo.duplicate", other.getName()));
            }
        });
        video.edit(request.name().trim(), request.url().trim(), parsed.key(),
                Strings.trimToNull(request.description()));
        return toResponse(repository.save(video));
    }

    /**
     * Only ever possible for a clip nobody uses. Anything already attached must be archived instead,
     * so the history of a completed session keeps the demonstration that belonged to it.
     */
    @Transactional
    public void delete(UUID id) {
        ExerciseVideo video = require(id);
        if (attachmentRepository.existsByVideoId(id)) {
            throw new IllegalStateException(msg.get("exercisevideo.in.use"));
        }
        repository.delete(video);
    }

    @Transactional
    public ExerciseVideoResponse setArchived(UUID id, boolean archived) {
        ExerciseVideo video = require(id);
        if (archived) {
            video.archive();
        } else {
            video.restore();
        }
        return toResponse(repository.save(video));
    }

    private ExerciseVideo require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("exercisevideo.not.found")));
    }

    private YouTubeUrl parseOrThrow(String url) {
        return YouTubeUrl.parse(url)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("exercisevideo.url.invalid")));
    }

    /** Matches how {@code search_text} is stored, so accents in the query do not break the match. */
    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String stripped = Normalizer.normalize(query.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.replace('ł', 'l').replace('Ł', 'L').toLowerCase(Locale.ROOT);
    }


    static ExerciseVideoResponse toResponse(ExerciseVideo v) {
        YouTubeUrl parsed = new YouTubeUrl(v.getVideoKey());
        return new ExerciseVideoResponse(v.getId(), v.getName(), v.getUrl(), v.getDescription(),
                parsed.embedUrl(), parsed.thumbnailUrl(), v.isArchived());
    }
}
