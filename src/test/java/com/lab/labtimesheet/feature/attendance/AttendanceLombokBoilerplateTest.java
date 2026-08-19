package com.lab.labtimesheet.feature.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.controller.AttendanceController;
import com.lab.labtimesheet.feature.attendance.controller.CalendarController;
import com.lab.labtimesheet.feature.attendance.controller.InternCorrectionController;
import com.lab.labtimesheet.feature.attendance.controller.InternLeaveController;
import com.lab.labtimesheet.feature.attendance.controller.MentorLeaveController;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceDayContext;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionsOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportForm;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.attendance.service.CorrectionService;
import com.lab.labtimesheet.feature.attendance.service.HolidayApiClient;
import com.lab.labtimesheet.feature.attendance.service.HolidayImportService;
import com.lab.labtimesheet.feature.attendance.service.LeaveService;
import com.lab.labtimesheet.feature.attendance.service.UnconfiguredHolidayApiClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

class AttendanceLombokBoilerplateTest {

    private static final int PACKAGE_PRIVATE = 0;

    @Test
    void generatedConstructorsPreserveParameterListsAndVisibility() {
        assertConstructors(
                AttendanceController.class,
                constructor(
                        PACKAGE_PRIVATE,
                        AttendanceApplicationService.class,
                        AttendanceCurrentUserService.class));
        assertConstructors(
                CalendarController.class,
                constructor(
                        PACKAGE_PRIVATE,
                        CalendarApplicationService.class,
                        HolidayImportService.class,
                        AttendanceApplicationService.class,
                        AttendanceCurrentUserService.class));
        assertConstructors(
                InternLeaveController.class,
                constructor(
                        PACKAGE_PRIVATE,
                        LeaveService.class,
                        AttendanceApplicationService.class,
                        AttendanceCurrentUserService.class));
        assertConstructors(
                InternCorrectionController.class,
                constructor(
                        PACKAGE_PRIVATE,
                        CorrectionService.class,
                        AttendanceApplicationService.class,
                        AttendanceCurrentUserService.class));
        assertConstructors(
                AttendanceApplicationService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AttendancePolicyRepository.class,
                        AttendanceRecordRepository.class,
                        AttendanceCorrectionRepository.class,
                        AttendanceQueryRepository.class,
                        AccountService.class,
                        CalendarApplicationService.class,
                        AttendanceService.class));
        assertConstructors(
                AttendanceCurrentUserService.class,
                constructor(PACKAGE_PRIVATE, AccountService.class));
        assertConstructors(
                CalendarApplicationService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AttendancePolicyRepository.class,
                        GlobalCalendarEventRepository.class));
        assertConstructors(
                AttendanceService.class, constructor(PACKAGE_PRIVATE));
        assertConstructors(
                HolidayImportService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        HolidayApiClient.class,
                        GlobalCalendarEventRepository.class));
        assertConstructors(UnconfiguredHolidayApiClient.class, constructor(PACKAGE_PRIVATE));
        assertConstructors(
                CorrectionService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AccountService.class,
                        AttendanceRecordRepository.class,
                        AttendanceCorrectionRepository.class,
                        AttendanceCorrectionEventRepository.class));
        assertConstructors(
                LeaveService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AccountService.class,
                        AttendancePolicyRepository.class,
                        CalendarApplicationService.class,
                        LeaveRequestRepository.class,
                        LeaveRequestDayRepository.class));

        assertConstructors(
                AttendancePolicyEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(Modifier.PUBLIC, AttendancePolicy.class, long.class));
        assertConstructors(
                AttendanceCorrectionEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        Modifier.PUBLIC,
                        AttendanceRecordEntity.class,
                        Instant.class,
                        String.class,
                        Instant.class,
                        Instant.class,
                        Instant.class));
        assertConstructors(
                AttendanceCorrectionEventEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        Modifier.PUBLIC,
                        long.class,
                        String.class,
                        String.class,
                        String.class,
                        Long.class,
                        String.class,
                        Instant.class));
        assertConstructors(
                AttendanceRecordEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        Modifier.PUBLIC,
                        long.class,
                        LocalDate.class,
                        AttendancePolicyEntity.class,
                        Instant.class,
                        Instant.class));
        assertConstructors(
                GlobalCalendarEventEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        Modifier.PUBLIC,
                        LocalDate.class,
                        String.class,
                        boolean.class,
                        long.class),
                constructor(
                        Modifier.PUBLIC,
                        LocalDate.class,
                        String.class,
                        HolidayCandidate.class,
                        boolean.class,
                        Instant.class,
                        long.class));
        assertConstructors(
                LeaveRequestDayEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        Modifier.PUBLIC,
                        LeaveRequestEntity.class,
                        LocalDate.class,
                        AttendancePolicyEntity.class,
                        int.class));
        assertConstructors(
                LeaveRequestDayId.class,
                constructor(Modifier.PROTECTED),
                constructor(Modifier.PUBLIC, long.class, LocalDate.class));
        assertConstructors(
                LeaveRequestEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        PACKAGE_PRIVATE,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class,
                        Instant.class,
                        long.class,
                        Instant.class),
                constructor(
                        Modifier.PRIVATE,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class,
                        Instant.class));
    }

    @Test
    void immutableModelsRemainRecordsWithTheirComponentContracts() {
        assertRecordComponents(
                AttendanceActor.class,
                component("userId", long.class),
                component("role", AttendanceRole.class));
        assertRecordComponents(
                AttendanceDayContext.class,
                component("activeIntern", boolean.class),
                component("globalDayOff", boolean.class),
                component("approvedLeave", boolean.class));
        assertRecordComponents(
                AttendancePolicy.class,
                component("id", long.class),
                component("effectiveFrom", LocalDate.class),
                component("zoneId", ZoneId.class),
                component("scheduledStart", LocalTime.class),
                component("scheduledEnd", LocalTime.class),
                component("checkInGraceMinutes", int.class),
                component("checkoutGraceMinutes", int.class),
                component("monthlyLeaveQuota", int.class),
                component("violationPenalty", BigDecimal.class),
                component("workdays", Set.class));
        assertRecordComponents(
                AttendanceRecord.class,
                component("internId", long.class),
                component("workDate", LocalDate.class),
                component("policy", AttendancePolicy.class),
                component("checkInAt", Instant.class),
                component("checkOutAt", Instant.class));
        assertRecordComponents(
                AttendanceViolations.class,
                component("late", boolean.class),
                component("earlyDeparture", boolean.class),
                component("missingCheckout", boolean.class));
        assertRecordComponents(
                AttendanceHistoryItem.class,
                component("workDate", LocalDate.class),
                component("checkInAt", Instant.class),
                component("checkOutAt", Instant.class),
                component("effectiveCheckOutAt", Instant.class),
                component("policy", AttendancePolicy.class),
                component("violations", AttendanceViolations.class));
        assertRecordComponents(
                CorrectionSubmission.class,
                component("id", long.class),
                component("workDate", LocalDate.class),
                component("proposedCheckoutAt", Instant.class),
                component("zoneId", ZoneId.class),
                component("reason", String.class),
                component("status", String.class),
                component("submittedAt", Instant.class),
                component("submissionDeadline", Instant.class),
                component("decisionDeadline", Instant.class));
        assertRecordComponents(
                CorrectionSubmissionCommand.class,
                component("workDate", LocalDate.class),
                component("proposedCheckoutTime", LocalTime.class),
                component("reason", String.class));
        assertRecordComponents(
                CorrectionsOverview.class,
                component("month", LocalDate.class),
                component("corrections", List.class));
        assertRecordComponents(
                GlobalCalendarEvent.class,
                component("id", long.class),
                component("date", LocalDate.class),
                component("name", String.class),
                component("dayOff", boolean.class),
                component("version", long.class),
                component("source", String.class),
                component("sourceUuid", String.class),
                component("actualDate", LocalDate.class),
                component("observedDate", LocalDate.class),
                component("publicHoliday", Boolean.class),
                component("importedAt", Instant.class));
    }

    @Test
    void entitiesExposeOnlyIntentionalPublicAndProtectedDeclaredMethods() {
        assertMethodSurface(
                AttendancePolicyEntity.class,
                method(Modifier.PUBLIC, "toDomain", AttendancePolicy.class),
                method(Modifier.PUBLIC, "update", void.class, AttendancePolicy.class),
                method(Modifier.PUBLIC, "effectiveFrom", LocalDate.class),
                method(Modifier.PUBLIC, "version", long.class));
        assertMethodSurface(
                AttendanceRecordEntity.class,
                method(Modifier.PUBLIC, "toDomain", AttendanceRecord.class),
                method(Modifier.PUBLIC, "setCheckOutAt", void.class, Instant.class),
                method(Modifier.PUBLIC, "id", long.class),
                method(Modifier.PUBLIC, "checkInAt", Instant.class),
                method(Modifier.PUBLIC, "checkOutAt", Instant.class),
                method(Modifier.PUBLIC, "workDate", LocalDate.class));
        assertMethodSurface(
                AttendanceCorrectionEntity.class,
                method(Modifier.PUBLIC, "id", long.class),
                method(Modifier.PUBLIC, "attendanceRecord", AttendanceRecordEntity.class),
                method(Modifier.PUBLIC, "requestedCheckoutAt", Instant.class),
                method(Modifier.PUBLIC, "reason", String.class),
                method(Modifier.PUBLIC, "status", String.class),
                method(Modifier.PUBLIC, "submittedAt", Instant.class),
                method(Modifier.PUBLIC, "submissionDeadline", Instant.class),
                method(Modifier.PUBLIC, "decisionDeadline", Instant.class),
                method(Modifier.PUBLIC, "approve", void.class, long.class, Instant.class, String.class),
                method(Modifier.PUBLIC, "decidedByMentorUserId", Long.class),
                method(Modifier.PUBLIC, "decidedAt", Instant.class),
                method(Modifier.PUBLIC, "decisionNote", String.class),
                method(Modifier.PUBLIC, "lockedAt", Instant.class));
        assertMethodSurface(
                AttendanceCorrectionEventEntity.class,
                method(Modifier.PUBLIC, "correctionId", long.class),
                method(Modifier.PUBLIC, "eventType", String.class),
                method(Modifier.PUBLIC, "fromStatus", String.class),
                method(Modifier.PUBLIC, "toStatus", String.class),
                method(Modifier.PUBLIC, "actorUserId", Long.class),
                method(Modifier.PUBLIC, "note", String.class),
                method(Modifier.PUBLIC, "occurredAt", Instant.class));
        assertMethodSurface(
                GlobalCalendarEventEntity.class,
                method(
                        Modifier.PUBLIC,
                        "update",
                        void.class,
                        LocalDate.class,
                        String.class,
                        boolean.class,
                        long.class),
                method(Modifier.PUBLIC, "toDomain", GlobalCalendarEvent.class),
                method(Modifier.PUBLIC, "calendarDate", LocalDate.class),
                method(Modifier.PUBLIC, "version", long.class));
        assertMethodSurface(
                LeaveRequestDayEntity.class,
                method(Modifier.PUBLIC, "leaveDate", LocalDate.class),
                method(Modifier.PUBLIC, "quotaMonth", LocalDate.class),
                method(Modifier.PUBLIC, "monthlyQuotaSnapshot", int.class));
        assertMethodSurface(
                LeaveRequestDayId.class,
                method(Modifier.PUBLIC, "equals", boolean.class, Object.class),
                method(Modifier.PUBLIC, "hashCode", int.class),
                method(Modifier.PUBLIC, "leaveDate", LocalDate.class));
        assertMethodSurface(
                LeaveRequestEntity.class,
                method(
                        Modifier.PUBLIC | Modifier.STATIC,
                        "pending",
                        LeaveRequestEntity.class,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class,
                        Instant.class),
                method(Modifier.PUBLIC, "id", long.class),
                method(Modifier.PUBLIC, "internUserId", long.class),
                method(Modifier.PUBLIC, "startDate", LocalDate.class),
                method(Modifier.PUBLIC, "endDate", LocalDate.class),
                method(Modifier.PUBLIC, "reason", String.class),
                method(Modifier.PUBLIC, "status", String.class),
                method(Modifier.PUBLIC, "submittedAt", Instant.class),
                method(Modifier.PUBLIC, "firstCountedStartAt", Instant.class),
                method(Modifier.PUBLIC, "approve", void.class, long.class, Instant.class, String.class),
                method(Modifier.PUBLIC, "reject", void.class, long.class, Instant.class, String.class),
                method(Modifier.PUBLIC, "cancel", void.class, Instant.class),
                method(
                        Modifier.PUBLIC,
                        "updateRange",
                        void.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class,
                        Instant.class),
                method(Modifier.PUBLIC, "decidedByMentorUserId", Long.class),
                method(Modifier.PUBLIC, "decidedAt", Instant.class),
                method(Modifier.PUBLIC, "decisionNote", String.class),
                method(Modifier.PUBLIC, "cancelledAt", Instant.class));
    }

    @Test
    void componentsExposeOnlyIntentionalPublicAndProtectedDeclaredMethods() {
        assertMethodSurface(
                AttendanceController.class,
                method(
                        Modifier.PUBLIC,
                        "ownHistory",
                        String.class,
                        Principal.class,
                        LocalDate.class,
                        LocalDate.class,
                        Model.class),
                method(
                        Modifier.PUBLIC,
                        "inspectHistory",
                        String.class,
                        Principal.class,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        Model.class),
                method(
                        Modifier.PUBLIC,
                        "checkIn",
                        String.class,
                        Principal.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "checkOut",
                        String.class,
                        Principal.class,
                        RedirectAttributes.class));
        assertMethodSurface(
                CalendarController.class,
                method(Modifier.PUBLIC, "calendar", String.class, Principal.class, Model.class),
                method(
                        Modifier.PUBLIC,
                        "create",
                        String.class,
                        Principal.class,
                        LocalDate.class,
                        String.class,
                        boolean.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "update",
                        String.class,
                        Principal.class,
                        long.class,
                        long.class,
                        LocalDate.class,
                        String.class,
                        boolean.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "holidays",
                        String.class,
                        Principal.class,
                        Integer.class,
                        Model.class),
                method(
                        Modifier.PUBLIC,
                        "importHolidays",
                        String.class,
                        Principal.class,
                        HolidayImportForm.class,
                        RedirectAttributes.class));
        assertMethodSurface(
                InternLeaveController.class,
                method(Modifier.PUBLIC, "form", String.class, Principal.class, Model.class),
                method(
                        Modifier.PUBLIC,
                        "submit",
                        String.class,
                        Principal.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "cancel",
                        String.class,
                        Principal.class,
                        long.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "edit",
                        String.class,
                        Principal.class,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        RedirectAttributes.class));
        assertMethodSurface(
                InternCorrectionController.class,
                method(Modifier.PUBLIC, "form", String.class, Principal.class, Model.class),
                method(
                        Modifier.PUBLIC,
                        "submit",
                        String.class,
                        Principal.class,
                        LocalDate.class,
                        LocalTime.class,
                        String.class,
                        RedirectAttributes.class));
        assertMethodSurface(
                MentorLeaveController.class,
                method(Modifier.PUBLIC, "form", String.class, Principal.class, Model.class),
                method(
                        Modifier.PUBLIC,
                        "approve",
                        String.class,
                        Principal.class,
                        long.class,
                        String.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "reject",
                        String.class,
                        Principal.class,
                        long.class,
                        String.class,
                        RedirectAttributes.class));
        assertMethodSurface(
                LeaveService.class,
                method(
                        Modifier.PUBLIC,
                        "submit",
                        LeaveSubmission.class,
                        long.class,
                        LeaveSubmissionCommand.class),
                method(
                        Modifier.PUBLIC,
                        "overview",
                        LeaveOverview.class,
                        long.class,
                        LocalDate.class),
                method(
                        Modifier.PUBLIC,
                        "decide",
                        LeaveSubmission.class,
                        long.class,
                        long.class,
                        LeaveDecisionCommand.class),
                method(Modifier.PUBLIC, "cancel", LeaveSubmission.class, long.class, long.class),
                method(
                        Modifier.PUBLIC,
                        "edit",
                        LeaveSubmission.class,
                        long.class,
                        long.class,
                        LeaveSubmissionCommand.class),
                method(Modifier.PUBLIC, "decisions", List.class));
        assertMethodSurface(
                CorrectionService.class,
                method(
                        Modifier.PUBLIC,
                        "submit",
                        CorrectionSubmission.class,
                        long.class,
                        CorrectionSubmissionCommand.class),
                method(
                        Modifier.PUBLIC,
                        "overview",
                        CorrectionsOverview.class,
                        long.class,
                        LocalDate.class));
        assertMethodSurface(
                AttendanceApplicationService.class,
                method(Modifier.PUBLIC, "checkIn", AttendanceRecord.class, long.class),
                method(Modifier.PUBLIC, "checkOut", AttendanceRecord.class, long.class),
                method(Modifier.PUBLIC, "currentState", AttendanceCurrentState.class, long.class),
                method(
                        Modifier.PUBLIC,
                        "history",
                        List.class,
                        AttendanceActor.class,
                        long.class,
                        LocalDate.class,
                        LocalDate.class),
                method(Modifier.PUBLIC, "currentBusinessDate", LocalDate.class));
        assertMethodSurface(
                AttendanceCurrentUserService.class,
                method(Modifier.PUBLIC, "actor", AttendanceActor.class, Principal.class));
        assertMethodSurface(
                AttendanceService.class,
                method(
                        Modifier.PUBLIC,
                        "checkIn",
                        AttendanceRecord.class,
                        long.class,
                        Instant.class,
                        AttendancePolicy.class,
                        AttendanceDayContext.class,
                        Optional.class),
                method(
                        Modifier.PUBLIC,
                        "checkOut",
                        AttendanceRecord.class,
                        Optional.class,
                        Instant.class));
        assertMethodSurface(
                CalendarApplicationService.class,
                method(
                        Modifier.PUBLIC,
                        "createManual",
                        GlobalCalendarEvent.class,
                        AttendanceActor.class,
                        LocalDate.class,
                        String.class,
                        boolean.class),
                method(
                        Modifier.PUBLIC,
                        "updateManual",
                        GlobalCalendarEvent.class,
                        AttendanceActor.class,
                        long.class,
                        long.class,
                        LocalDate.class,
                        String.class,
                        boolean.class),
                method(Modifier.PUBLIC, "list", List.class, LocalDate.class, LocalDate.class),
                method(Modifier.PUBLIC, "isGlobalDayOff", boolean.class, LocalDate.class));
        assertMethodSurface(
                HolidayImportService.class,
                method(
                        Modifier.PUBLIC,
                        "preview",
                        List.class,
                        AttendanceActor.class,
                        int.class),
                method(
                        Modifier.PUBLIC,
                        "importSelections",
                        HolidayImportSummary.class,
                        AttendanceActor.class,
                        int.class,
                        List.class));
        assertMethodSurface(
                UnconfiguredHolidayApiClient.class,
                method(Modifier.PUBLIC, "fetchVnHolidays", List.class, int.class));
    }

    private static void assertConstructors(
            Class<?> type, ConstructorContract... expectedConstructors) {
        List<ConstructorContract> actual = Arrays.stream(type.getDeclaredConstructors())
                .map(ConstructorContract::from)
                .toList();

        assertThat(actual).as(type.getName()).containsExactlyInAnyOrder(expectedConstructors);
    }

    private static void assertRecordComponents(
            Class<?> type, RecordComponentContract... expectedComponents) {
        assertThat(type.isRecord()).as(type.getName()).isTrue();
        assertThat(Arrays.stream(type.getRecordComponents())
                        .map(component -> new RecordComponentContract(component.getName(), component.getType()))
                        .toList())
                .as(type.getName())
                .containsExactly(expectedComponents);
    }

    private static void assertMethodSurface(Class<?> type, MethodContract... expectedMethods) {
        List<MethodContract> actual = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .map(MethodContract::from)
                .toList();

        assertThat(actual).as(type.getName()).containsExactlyInAnyOrder(expectedMethods);
    }

    private static ConstructorContract constructor(int modifiers, Class<?>... parameterTypes) {
        return new ConstructorContract(modifiers, List.of(parameterTypes));
    }

    private static RecordComponentContract component(String name, Class<?> type) {
        return new RecordComponentContract(name, type);
    }

    private static MethodContract method(
            int modifiers, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return new MethodContract(modifiers, name, returnType, List.of(parameterTypes));
    }

    private record ConstructorContract(int modifiers, List<Class<?>> parameterTypes) {

        private static ConstructorContract from(Constructor<?> constructor) {
            return new ConstructorContract(
                    constructor.getModifiers(), List.of(constructor.getParameterTypes()));
        }
    }

    private record RecordComponentContract(String name, Class<?> type) {}

    private record MethodContract(
            int modifiers, String name, Class<?> returnType, List<Class<?>> parameterTypes) {

        private static MethodContract from(Method method) {
            return new MethodContract(
                    method.getModifiers(),
                    method.getName(),
                    method.getReturnType(),
                    List.of(method.getParameterTypes()));
        }
    }
}
