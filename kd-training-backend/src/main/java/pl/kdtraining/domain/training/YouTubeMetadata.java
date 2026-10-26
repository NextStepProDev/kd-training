package pl.kdtraining.domain.training;

import org.jspecify.annotations.Nullable;

/**
 * What YouTube says about a clip, or why it would not say.
 *
 * @param status     never null — the caller has to handle "we could not ask" as its own case
 * @param title      the video's own title, for prefilling the name
 * @param authorName the channel, shown so it is obvious which clip was found
 */
public record YouTubeMetadata(Status status, @Nullable String title, @Nullable String authorName) {

    public enum Status {
        OK,
        /**
         * Private, deleted, or embedding disabled. Worth its own value: this is exactly the clip
         * that would otherwise be saved happily and show a client an empty player a week later.
         */
        UNAVAILABLE,
        /** YouTube did not answer in time or answered with something unexpected. Not the user's fault. */
        UNKNOWN,
    }

    public static YouTubeMetadata ok(String title, @Nullable String authorName) {
        return new YouTubeMetadata(Status.OK, title, authorName);
    }

    public static YouTubeMetadata unavailable() {
        return new YouTubeMetadata(Status.UNAVAILABLE, null, null);
    }

    public static YouTubeMetadata unknown() {
        return new YouTubeMetadata(Status.UNKNOWN, null, null);
    }
}
