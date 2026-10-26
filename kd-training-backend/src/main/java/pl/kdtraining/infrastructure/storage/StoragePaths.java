package pl.kdtraining.infrastructure.storage;

import java.util.regex.Pattern;

/**
 * What a folder and a filename are allowed to look like anywhere under the storage root.
 * <p>
 * Every stored name is minted by {@link LocalFileStorageService#storeImage} as a random UUID plus an
 * extension, and every name read back comes from a database column that was filled that way. So this
 * is not a filter over hostile input today — it is the layer that decides what happens on the day a
 * name arrives from somewhere else. Without it, {@code root.resolve(folder).resolve(filename)} will
 * happily walk out of the storage root on a name containing {@code ..}, and nothing between the
 * request and the filesystem would object.
 * <p>
 * The patterns live here rather than in each caller because there are two callers with the same
 * rule and different jobs: {@code FileController} turns a bad name into 400 before it reaches
 * storage, and {@link LocalFileStorageService} refuses it outright. Two copies of one security rule
 * drift, and the copy that falls behind is the one nobody is looking at.
 */
public final class StoragePaths {

    /** Folder names are chosen in code, never by a user; all of them are plain lowercase words. */
    public static final Pattern FOLDER = Pattern.compile("^[a-z]+$");

    /**
     * Exactly what {@code storeImage} writes: a lowercase random UUID and an extension we encode to.
     * {@code jpeg} earns its place because an already-optimized upload keeps the extension it came
     * with, so a {@code .jpeg} that needed no re-encoding reaches disk unchanged.
     */
    public static final Pattern FILENAME = Pattern.compile(
        "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|jpeg|png|webp)$");

    private StoragePaths() {
    }

    /**
     * @throws IllegalArgumentException if either part is not a name this service could have written.
     *         Thrown rather than answered with a quiet {@code false}, so that a malformed name reads
     *         as the mistake it is instead of looking like a file that is merely missing.
     */
    public static void require(String folder, String filename) {
        requireFolder(folder);
        if (filename == null || !FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("Invalid storage filename: " + filename);
        }
    }

    /** Folder alone — for the write paths, which mint the filename themselves. */
    public static void requireFolder(String folder) {
        if (folder == null || !FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Invalid storage folder: " + folder);
        }
    }
}
