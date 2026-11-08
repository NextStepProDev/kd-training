package pl.kdtraining.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CoachDayOffRepository extends JpaRepository<CoachDayOff, UUID> {

    /** Every day off touching [from, to] — a holiday that started before the window still shows in it. */
    @Query("""
        SELECT d FROM CoachDayOff d
        WHERE d.startDate <= :to AND d.endDate >= :from
        ORDER BY d.startDate
        """)
    List<CoachDayOff> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The coach's list: what is still ahead or going on, soonest first. Past ones are history. */
    @Query("""
        SELECT d FROM CoachDayOff d
        WHERE d.endDate >= :today
        ORDER BY d.startDate
        """)
    List<CoachDayOff> findCurrentAndUpcoming(@Param("today") LocalDate today);
}
