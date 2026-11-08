package pl.mocnygrzbiet.infrastructure.mail;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * What one "your plan changed" mail says: the trainings the coach added, moved or edited, and
 * cancelled since the last such mail. Dates and hours only — never a title or a description, for the
 * same reason the coach-reply mail carries no message: a plan can be about an injury, and a mailbox
 * is read on lock screens and shared computers.
 */
public record TrainingChangeDigest(List<Slot> added, List<Changed> changed, List<Slot> cancelled) {

    public TrainingChangeDigest {
        added = added.stream().sorted().toList();
        changed = changed.stream().sorted(Comparator.comparing(Changed::now)).toList();
        cancelled = cancelled.stream().sorted().toList();
    }

    public boolean isEmpty() {
        return added.isEmpty() && changed.isEmpty() && cancelled.isEmpty();
    }

    /** When a training takes place. No hour is the normal case: "do this that day". */
    public record Slot(LocalDate date, @Nullable LocalTime start) implements Comparable<Slot> {

        private static final Comparator<Slot> ORDER = Comparator.comparing(Slot::date)
                .thenComparing(Slot::start, Comparator.nullsFirst(Comparator.naturalOrder()));

        /**
         * Still ahead of the client: a later day, or today before its hour. An untimed training counts
         * for the whole of its day — "do this today" is still news at 20:00.
         */
        public boolean isUpcoming(LocalDateTime now) {
            if (!date.equals(now.toLocalDate())) {
                return date.isAfter(now.toLocalDate());
            }
            return start == null || start.isAfter(now.toLocalTime());
        }

        @Override
        public int compareTo(Slot other) {
            return ORDER.compare(this, other);
        }
    }

    /**
     * A training that is still on, in its current slot. {@code before} is the slot the client last
     * heard about, present only when it differs: an edit of the description alone moves nothing.
     */
    public record Changed(Slot now, @Nullable Slot before) {}
}
