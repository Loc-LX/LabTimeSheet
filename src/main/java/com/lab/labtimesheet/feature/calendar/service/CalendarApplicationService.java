package com.lab.labtimesheet.feature.calendar.service;

import com.lab.labtimesheet.feature.calendar.exception.CalendarException;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.calendar.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.calendar.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.calendar.model.dto.CalendarPreviewItem;
import com.lab.labtimesheet.feature.calendar.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.calendar.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.calendar.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.calendar.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.calendar.repository.GlobalCalendarEventRepository;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.calendar.service.HolidayApiConfigurationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional boundary for the locally authoritative global calendar and its cross-feature day-off decision.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CalendarApplicationService {

    private final Clock clock;
    private final AccountService accounts;
    private final AttendancePolicyRepository policies;
    private final GlobalCalendarEventRepository events;
    private final HolidayApiConfigurationService holidayApi;
    private final TransactionTemplate transactions;

    /**
     * Resolves the current business date in the timezone of the effective policy owned by calendar.
     *
     * @return current policy-local date from the injected server clock
     */
    @Transactional(readOnly = true)
    public LocalDate currentBusinessDate() {
        AttendancePolicy policy = policyTimeline().resolve(clock.instant());
        return clock.instant().atZone(policy.zoneId()).toLocalDate();
    }

    /**
     * Creates an Admin-authored custom event on a non-past policy-local date.
     *
     * @param adminId authenticated Admin account identifier
     * @param date local event date
     * @param name non-blank display name
     * @param dayOff authoritative attendance/due-date exemption choice
     * @return persisted event DTO including optimistic version
     */
    @Transactional
    public GlobalCalendarEvent createManual(
            long adminId, LocalDate date, String name, boolean dayOff) {
        long verifiedAdminId = requireActiveAdminId(adminId);
        requireMutableDate(date);
        return events.saveAndFlush(new GlobalCalendarEventEntity(
                        date, requireName(name), dayOff, verifiedAdminId, clock.instant()))
                .toDomain();
    }

    /**
     * Performs the explicit Admin-only external preview action through Platform's public service.
     * Local calendar reads and attendance decisions never call this method.
     *
     * @param adminId authenticated Admin account identifier
     * @param year requested Vietnam calendar year
     * @return immutable provider result with actionable, secret-free status metadata
     */
    public HolidayApiPreview previewFromProvider(long adminId, int year) {
        long verifiedAdminId = requireActiveAdminId(adminId);
        requireYear(year);
        return holidayApi.preview(verifiedAdminId, year);
    }

    /**
     * Interprets one successful Platform preview for explicit Admin selection.
     * Provider failures remain represented by the returned Platform preview and therefore cannot be mistaken for an
     * empty successful import preview.
     *
     * @param adminId authenticated Admin account identifier
     * @param year requested Vietnam calendar year
     * @param upstream immutable Platform preview result
     * @return deduplicated local review rows, or an empty list for an actionable non-success result
     */
    @Transactional(readOnly = true)
    public List<CalendarPreviewItem> preview(
            long adminId, int year, HolidayApiPreview upstream) {
        requireActiveAdminId(adminId);
        requireYear(year);
        Objects.requireNonNull(upstream, "HolidayAPI preview is required");
        if (upstream.status() != HolidayApiPreviewStatus.SUCCESS) {
            return List.of();
        }
        Instant retrievedAt = Objects.requireNonNull(
                upstream.retrievedAt(), "Successful HolidayAPI preview retrieval instant is required");
        Set<String> seenSourceUuids = new HashSet<>();
        return upstream.candidates().stream()
                .map(candidate -> requireCandidate(candidate, year))
                .filter(candidate -> seenSourceUuids.add(canonicalSourceUuid(candidate)))
                .map(candidate -> new CalendarPreviewItem(candidate, candidate.publicHoliday(), retrievedAt))
                .toList();
    }

    /**
     * Copies explicitly selected source identities using a fresh successful Platform preview.
     *
     * <p>The client supplies only source UUIDs and local day-off decisions. Candidate fields and the retrieval instant
     * are obtained from Platform in this server-side call, so request-bound calendar data cannot become local
     * HolidayAPI provenance.</p>
     *
     * @param adminId authenticated Admin account identifier
     * @param year requested Vietnam calendar year used to validate every candidate date
     * @param selections source identities and explicit local day-off decisions
     * @return newly imported local history rows; an empty result means every selection was already imported
     */
    public List<CalendarHistoryItem> importSelected(
            long adminId, int year, List<CalendarImportSelection> selections) {
        long verifiedAdminId = requireActiveAdminId(adminId);
        requireYear(year);
        HolidayApiPreview trustedPreview = holidayApi.preview(verifiedAdminId, year);
        return importSelectedFromTrustedPreview(verifiedAdminId, year, trustedPreview, selections);
    }

    /**
     * Copies explicitly selected source identities from a server-trusted Platform preview snapshot.
     *
     * <p>This transaction is the local persistence boundary. The preview must be the immutable result returned by
     * {@link #previewFromProvider(long, int)} or an equivalent server-side snapshot; it must never be
     * request-bound. Existing rows are never overwritten by a repeated source UUID.</p>
     *
     * @param adminId authenticated Admin account identifier
     * @param year requested Vietnam calendar year used to validate every candidate date
     * @param trustedPreview successful Platform preview retained by the server
     * @param selections source identities and explicit local day-off decisions
     * @return newly imported local history rows; an empty result means every selection was already imported
     */
    List<CalendarHistoryItem> importSelectedFromTrustedPreview(
            long adminId,
            int year,
            HolidayApiPreview trustedPreview,
            List<CalendarImportSelection> selections) {
        long verifiedAdminId = requireActiveAdminId(adminId);
        requireYear(year);
        if (selections == null) {
            throw new IllegalArgumentException("Calendar selections are required");
        }
        Map<String, HolidayApiCandidate> candidates = trustedCandidates(trustedPreview, year);
        return independentTransactions().execute(status -> importInTransaction(
                verifiedAdminId, year, trustedPreview.retrievedAt(), candidates, selections));
    }

    private List<CalendarHistoryItem> importInTransaction(
            long adminId,
            int year,
            Instant retrievedAt,
            Map<String, HolidayApiCandidate> candidates,
            List<CalendarImportSelection> selections) {
        policies.findFirstByOrderByEffectiveFromAsc()
                .orElseThrow(() -> new IllegalStateException("Attendance policy seed is required"));
        return selections.stream()
                .sorted(Comparator.comparing(selection -> canonicalSourceUuid(requireSelection(selection).sourceUuid())))
                .map(selection -> importOne(adminId, year, retrievedAt, candidates, selection))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * Lists all retained past/current/future event metadata for the Admin History view.
     *
     * @param adminId authenticated Admin account identifier
     * @return retained events ordered by local date and version
     */
    @Transactional(readOnly = true)
    public List<CalendarHistoryItem> history(long adminId) {
        requireActiveAdminId(adminId);
        return events.findAll().stream()
                .map(GlobalCalendarEventEntity::toHistory)
                .sorted(Comparator.comparing(CalendarHistoryItem::calendarDate)
                        .thenComparingLong(CalendarHistoryItem::id))
                .toList();
    }

    /**
     * Updates a future custom event when the submitted optimistic version still matches.
     * Past event dates and attempts to move an event into the past are rejected.
     *
     * @param adminId authenticated Admin account identifier
     * @param eventId event identifier
     * @param expectedVersion version rendered to the editor
     * @param date replacement local event date
     * @param name replacement non-blank display name
     * @param dayOff replacement authoritative day-off choice
     * @return updated event DTO and advanced version
     */
    @Transactional
    public GlobalCalendarEvent updateManual(
            long adminId,
            long eventId,
            long expectedVersion,
            LocalDate date,
            String name,
            boolean dayOff) {
        long verifiedAdminId = requireActiveAdminId(adminId);
        GlobalCalendarEventEntity event = events.findById(eventId)
                .orElseThrow(() -> new CalendarException("Calendar event not found"));
        requireMutableDate(event.calendarDate());
        requireMutableDate(date);
        if (event.version() != expectedVersion) {
            throw new CalendarException("Calendar event was changed by another request");
        }
        event.update(date, requireName(name), dayOff, verifiedAdminId, clock.instant());
        return events.saveAndFlush(event).toDomain();
    }

    /**
     * Lists locally stored events over an inclusive date range.
     *
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return events ordered by date and identifier
     */
    @Transactional(readOnly = true)
    public List<GlobalCalendarEvent> list(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return events.findByCalendarDateBetweenOrderByCalendarDateAscIdAsc(from, to)
                .stream()
                .map(GlobalCalendarEventEntity::toDomain)
                .toList();
    }

    /**
     * Answers whether the exact local date has any authoritative stored day-off event.
     * This is the public cross-feature calendar API; it performs no live HolidayAPI call.
     *
     * @param date local business date to inspect
     * @return {@code true} when at least one local event is marked as a day off
     */
    @Transactional(readOnly = true)
    public boolean isGlobalDayOff(LocalDate date) {
        return events.existsByCalendarDateAndDayOffTrue(date);
    }

    /**
     * Returns the complete effective-dated policy timeline for a consumer that must resolve a local date.
     *
     * @return immutable policy timeline in effective-date order
     */
    @Transactional(readOnly = true)
    public AttendancePolicyTimeline policyTimeline() {
        return new AttendancePolicyTimeline(policies.findAllByOrderByEffectiveFromAsc().stream()
                .map(AttendancePolicyEntity::toDomain)
                .toList());
    }

    /**
     * Reads the historical policy versions needed to rehydrate a bounded set of attendance rows.
     *
     * @param policyVersionIds identifiers collected before the consumer iterates its rows
     * @return policies indexed by immutable version identifier
     */
    @Transactional(readOnly = true)
    public Map<Long, AttendancePolicy> policiesByVersionIds(Set<Long> policyVersionIds) {
        if (policyVersionIds.isEmpty()) {
            return Map.of();
        }
        return policies.findAllById(policyVersionIds).stream()
                .map(AttendancePolicyEntity::toDomain)
                .collect(java.util.stream.Collectors.toMap(AttendancePolicy::id, policy -> policy));
    }

    /**
     * Locks the policy versions that provide quota snapshots before a leave allocation is written.
     *
     * @param policyVersionIds identifiers to lock in ascending order
     */
    @Transactional
    public void lockPolicyVersions(Set<Long> policyVersionIds) {
        for (Long policyVersionId : policyVersionIds.stream().sorted().toList()) {
            policies.findForUpdateById(policyVersionId)
                    .orElseThrow(() -> new IllegalStateException("Attendance policy version not found"));
        }
    }

    /**
     * Returns retained calendar-event history over an inclusive range without contacting HolidayAPI.
     *
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return events in date and identifier order
     */
    @Transactional(readOnly = true)
    public List<CalendarHistoryItem> historyBetween(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return events.findByCalendarDateBetweenOrderByCalendarDateAscIdAsc(from, to).stream()
                .map(GlobalCalendarEventEntity::toHistory)
                .toList();
    }

    private void requireMutableDate(LocalDate date) {
        AttendancePolicy policy = new AttendancePolicyTimeline(policies
                        .findAllByOrderByEffectiveFromAsc()
                        .stream()
                        .map(AttendancePolicyEntity::toDomain)
                        .toList())
                .resolve(clock.instant());
        LocalDate today = clock.instant().atZone(policy.zoneId()).toLocalDate();
        if (date.isBefore(today)) {
            throw new CalendarException("Past calendar events are immutable");
        }
    }

    private long requireActiveAdminId(long adminId) {
        try {
            return accounts.requireActiveAdminId(adminId);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Only Admin may manage the global calendar", exception);
        }
    }

    private java.util.Optional<CalendarHistoryItem> importOne(
            long adminId,
            int year,
            Instant retrievedAt,
            Map<String, HolidayApiCandidate> candidates,
            CalendarImportSelection selection) {
        CalendarImportSelection checked = requireSelection(selection);
        String sourceUuid = canonicalSourceUuid(checked.sourceUuid());
        HolidayApiCandidate candidate = candidates.get(sourceUuid);
        if (candidate == null) {
            throw new CalendarException("Selected HolidayAPI candidate was not present in the trusted preview");
        }
        if (events.findForUpdateBySourceAndSourceUuid("HOLIDAY_API", sourceUuid).isPresent()) {
            return java.util.Optional.empty();
        }
        requireMutableDate(candidate.observedDate());
        GlobalCalendarEventEntity entity = events.saveAndFlush(new GlobalCalendarEventEntity(
                candidate, checked.dayOff(), adminId, retrievedAt, clock.instant()));
        return java.util.Optional.of(entity.toHistory());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.strip();
    }

    private static CalendarImportSelection requireSelection(CalendarImportSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Calendar selection is required");
        }
        return selection;
    }

    private static Map<String, HolidayApiCandidate> trustedCandidates(
            HolidayApiPreview trustedPreview, int year) {
        Objects.requireNonNull(trustedPreview, "Trusted HolidayAPI preview is required");
        if (trustedPreview.status() != HolidayApiPreviewStatus.SUCCESS) {
            throw new CalendarException("A successful HolidayAPI preview is required before import: "
                    + trustedPreview.message());
        }
        Instant retrievedAt = Objects.requireNonNull(
                trustedPreview.retrievedAt(), "Successful HolidayAPI preview retrieval instant is required");
        Map<String, HolidayApiCandidate> candidates = new java.util.HashMap<>();
        trustedPreview.candidates().stream()
                .map(candidate -> requireCandidate(candidate, year))
                .forEach(candidate -> candidates.putIfAbsent(canonicalSourceUuid(candidate), candidate));
        return candidates;
    }

    private static HolidayApiCandidate requireCandidate(HolidayApiCandidate candidate, int year) {
        if (candidate == null) {
            throw new IllegalArgumentException("Holiday candidate is required");
        }
        String sourceUuid = canonicalSourceUuid(candidate);
        if (candidate.name() == null || candidate.name().isBlank()) {
            throw new IllegalArgumentException("Holiday candidate name is required");
        }
        if (candidate.actualDate() == null || candidate.observedDate() == null
                || candidate.actualDate().getYear() != year || candidate.observedDate().getYear() != year) {
            throw new IllegalArgumentException("Holiday candidate dates must belong to the requested year");
        }
        if (!sourceUuid.equals(candidate.uuid())
                || !candidate.name().equals(candidate.name().strip())) {
            return new HolidayApiCandidate(
                    sourceUuid, candidate.name().strip(), candidate.actualDate(), candidate.observedDate(),
                    candidate.publicHoliday());
        }
        return candidate;
    }

    private static String canonicalSourceUuid(HolidayApiCandidate candidate) {
        return canonicalSourceUuid(candidate == null ? null : candidate.uuid());
    }

    private static String canonicalSourceUuid(String sourceUuid) {
        if (sourceUuid == null || sourceUuid.isBlank()) {
            throw new IllegalArgumentException("Holiday candidate source UUID is required");
        }
        return sourceUuid.strip();
    }

    private TransactionTemplate independentTransactions() {
        TransactionTemplate independent = new TransactionTemplate(transactions.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent;
    }

    private static void requireYear(int year) {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("HolidayAPI year must be between 1 and 9999");
        }
    }
}
