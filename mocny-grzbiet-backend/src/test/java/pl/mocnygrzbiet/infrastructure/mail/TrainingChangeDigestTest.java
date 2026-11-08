package pl.mocnygrzbiet.infrastructure.mail;

import org.junit.jupiter.api.Test;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest.Slot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainingChangeDigestTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);

    @Test
    void shouldTreatAnUntimedTrainingAsUpcomingForTheWholeOfItsDay() {
        Slot untimed = new Slot(DAY, null);

        assertTrue(untimed.isUpcoming(DAY.minusDays(1).atTime(23, 0)));
        assertTrue(untimed.isUpcoming(DAY.atTime(22, 0)), "\"do this today\" is still news in the evening");
        assertFalse(untimed.isUpcoming(DAY.plusDays(1).atStartOfDay()));
    }

    @Test
    void shouldTreatATimedTrainingAsUpcomingUntilItsHour() {
        Slot evening = new Slot(DAY, LocalTime.of(18, 0));

        assertTrue(evening.isUpcoming(DAY.atTime(12, 0)));
        assertFalse(evening.isUpcoming(DAY.atTime(18, 0)));
        assertFalse(evening.isUpcoming(LocalDateTime.of(DAY.plusDays(1), LocalTime.of(9, 0))));
    }

    @Test
    void shouldListSlotsInCalendarOrderWithTheUntimedFirstOnTheirDay() {
        Slot later = new Slot(DAY.plusDays(1), LocalTime.of(8, 0));
        Slot evening = new Slot(DAY, LocalTime.of(18, 0));
        Slot untimed = new Slot(DAY, null);

        TrainingChangeDigest digest = new TrainingChangeDigest(List.of(later, evening, untimed), List.of(), List.of());

        assertEquals(List.of(untimed, evening, later), digest.added());
    }
}
