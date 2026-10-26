package pl.kdtraining.api.trainingcalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.domain.training.TrainingComment;
import pl.kdtraining.domain.training.TrainingCommentRepository;
import pl.kdtraining.infrastructure.storage.FileStorageService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Keeps the training photo folder honest, in two passes.
 * <p>
 * Photos are health data, and the retention promised in the privacy policy is 30 days — a promise
 * nothing else in the service would keep. The second pass is the one that matters for an audit:
 * every explicit unlink elsewhere happens inside a transaction that can still roll back, and
 * {@code LocalFileStorageService.delete} logs its failures rather than raising them. Reconciling the
 * folder against the rows turns every one of those races from a permanent leak of somebody's health
 * data into a file that survives at most one more night.
 * <p>
 * Both passes live here rather than on {@code TrainingPhotoRetentionScheduler} so that the only way
 * to reach them is from another bean, through the proxy that gives {@code @Transactional} its
 * meaning. A pass called from a sibling method of the scheduler would run without one, silently.
 */
@Service
public class TrainingPhotoRetentionService {

    private static final Logger log = LoggerFactory.getLogger(TrainingPhotoRetentionService.class);

    /**
     * A file younger than this is left alone by the orphan sweep. The set of known filenames is read
     * before the folder is listed, so an upload landing between the two would look orphaned — and
     * deleting a photo somebody just sent is far worse than sweeping it a day later.
     */
    private static final Duration ORPHAN_GRACE = Duration.ofHours(1);

    private final TrainingCommentRepository commentRepository;
    private final FileStorageService storage;

    public TrainingPhotoRetentionService(TrainingCommentRepository commentRepository,
                                         FileStorageService storage) {
        this.commentRepository = commentRepository;
        this.storage = storage;
    }

    /**
     * Retention pass. The words stay — only the picture goes, because a comment reading "legs were
     * dead today" is still worth having a year on. A comment that was nothing but a photo has
     * nothing left to say and is removed whole; the CHECK constraint would refuse it anyway.
     */
    @Transactional
    public int deleteExpired() {
        List<TrainingComment> expired = commentRepository.findExpiredPhotos(Instant.now());
        for (TrainingComment comment : expired) {
            String filename = comment.getPhotoFilename();
            if (comment.getBody() == null) {
                commentRepository.delete(comment);
            } else {
                comment.clearPhoto();
                commentRepository.save(comment);
            }
            if (filename != null) {
                storage.delete(TrainingPhotoService.FOLDER, filename);
            }
        }
        return expired.size();
    }

    /** Files on disk that no row claims any more — the safety net under every other delete path. */
    @Transactional(readOnly = true)
    public int deleteOrphans() {
        Set<String> known = commentRepository.findAllPhotoFilenames();
        Instant cutoff = Instant.now().minus(ORPHAN_GRACE);

        int removed = 0;
        for (String filename : storage.listFilenames(TrainingPhotoService.FOLDER)) {
            if (known.contains(filename)) {
                continue;
            }
            try {
                if (storage.getLastModified(TrainingPhotoService.FOLDER, filename).isAfter(cutoff)) {
                    continue;
                }
                storage.delete(TrainingPhotoService.FOLDER, filename);
                removed++;
            } catch (IllegalArgumentException e) {
                // A name this service never wrote — nothing put it there but a hand. Skipped rather
                // than deleted, because we do not destroy files we cannot account for; caught rather
                // than propagated, because one stray name must not stop the pass that removes
                // everybody else's expired health data.
                log.warn("Skipping unrecognised file in {}: {}", TrainingPhotoService.FOLDER, filename);
            }
        }
        return removed;
    }
}
