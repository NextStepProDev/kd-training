package pl.mocnygrzbiet.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mocnygrzbiet.api.NotFoundException;
import pl.mocnygrzbiet.api.Strings;
import pl.mocnygrzbiet.api.trainingcalendar.TrainingCalendarDtos.DayOffRequest;
import pl.mocnygrzbiet.api.trainingcalendar.TrainingCalendarDtos.DayOffResponse;
import pl.mocnygrzbiet.domain.training.CoachDayOff;
import pl.mocnygrzbiet.domain.training.CoachDayOffRepository;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The coach's days off, marked once and shown in every client's calendar (the client's answer to
 * "holidays in the schedule, so clients see a session will not take place": "to byłoby ok").
 */
@Service
public class CoachDayOffService {

    /** A quarter of a year. Longer than that is a typo in the year, not a holiday. */
    static final long MAX_SPAN_DAYS = 92;

    private final CoachDayOffRepository repository;
    private final MessageService msg;

    public CoachDayOffService(CoachDayOffRepository repository, MessageService msg) {
        this.repository = repository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<DayOffResponse> upcoming() {
        return repository.findCurrentAndUpcoming(LocalDate.now()).stream().map(CoachDayOffService::toResponse).toList();
    }

    /** For the calendar page; the caller has already checked who may see the range. */
    @Transactional(readOnly = true)
    public List<DayOffResponse> overlapping(LocalDate from, LocalDate to) {
        return repository.findOverlapping(from, to).stream().map(CoachDayOffService::toResponse).toList();
    }

    @Transactional
    public DayOffResponse create(DayOffRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException(msg.get("dayoff.range.invalid"));
        }
        if (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 > MAX_SPAN_DAYS) {
            throw new IllegalArgumentException(msg.get("dayoff.range.too.long"));
        }
        CoachDayOff saved = repository.save(
                new CoachDayOff(request.startDate(), request.endDate(), Strings.trimToNull(request.note())));
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CoachDayOff found = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("dayoff.not.found")));
        repository.delete(found);
    }

    private static DayOffResponse toResponse(CoachDayOff d) {
        return new DayOffResponse(d.getId(), d.getStartDate(), d.getEndDate(), d.getNote());
    }
}
