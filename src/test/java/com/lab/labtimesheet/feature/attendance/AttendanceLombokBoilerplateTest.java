package com.lab.labtimesheet.feature.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.controller.AttendanceController;
import com.lab.labtimesheet.feature.attendance.controller.CalendarController;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceDayContext;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceDeadlineScheduler;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyApplicationService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.support.TransactionTemplate;

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
                        AttendanceApplicationService.class,
                        AttendanceCurrentUserService.class));
        assertConstructors(
                AttendanceApplicationService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AttendancePolicyRepository.class,
                        AttendanceRecordRepository.class,
                        AttendanceQueryRepository.class,
                        AccountService.class,
                        CalendarApplicationService.class,
                        AttendanceService.class,
                        AttendanceCorrectionApplicationService.class));
        assertConstructors(
                AttendanceCurrentUserService.class,
                constructor(PACKAGE_PRIVATE, AccountService.class));
        assertConstructors(
                CalendarApplicationService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AttendancePolicyRepository.class,
                        GlobalCalendarEventRepository.class,
                        HolidayApiConfigurationService.class,
                        TransactionTemplate.class));
        assertConstructors(AttendanceService.class, constructor(PACKAGE_PRIVATE));
        assertConstructors(
                AttendancePolicyApplicationService.class,
                constructor(PACKAGE_PRIVATE, Clock.class, AttendancePolicyRepository.class));
        assertConstructors(
                LeaveApplicationService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AttendancePolicyRepository.class,
                        LeaveRequestRepository.class,
                        LeaveRequestDayRepository.class,
                        AccountService.class,
                        CalendarApplicationService.class,
                        TransactionTemplate.class,
                        NotificationService.class));
        assertConstructors(
                AttendanceCorrectionApplicationService.class,
                constructor(
                        PACKAGE_PRIVATE,
                        Clock.class,
                        AttendanceRecordRepository.class,
                        AttendanceCorrectionRepository.class,
                        AttendanceCorrectionEventRepository.class,
                        AccountService.class,
                        TransactionTemplate.class,
                        NotificationService.class));
        assertConstructors(
                AttendanceDeadlineScheduler.class,
                constructor(
                        PACKAGE_PRIVATE,
                        LeaveApplicationService.class,
                        AttendanceCorrectionApplicationService.class));

        assertConstructors(
                AttendancePolicyEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(Modifier.PUBLIC, AttendancePolicyCommand.class, long.class, Instant.class));
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
                        boolean.class,
                        long.class,
                        Instant.class),
                constructor(
                        Modifier.PUBLIC,
                        HolidayApiCandidate.class,
                        boolean.class,
                        long.class,
                        Instant.class,
                        Instant.class));
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
                        Modifier.PUBLIC,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class,
                        Instant.class),
                constructor(
                        PACKAGE_PRIVATE,
                        long.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class,
                        Instant.class,
                        long.class,
                        Instant.class));
        assertConstructors(
                AttendanceCorrectionEntity.class,
                constructor(Modifier.PROTECTED),
                constructor(
                        Modifier.PUBLIC,
                        long.class,
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
                        com.lab.labtimesheet.feature.attendance.model.CorrectionEventType.class,
                        CorrectionStatus.class,
                        CorrectionStatus.class,
                        Long.class,
                        String.class,
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
                component("policy", AttendancePolicy.class),
                component("violations", AttendanceViolations.class),
                component("attendanceRecordId", long.class));
        assertRecordComponents(
                GlobalCalendarEvent.class,
                component("id", long.class),
                component("date", LocalDate.class),
                component("name", String.class),
                component("dayOff", boolean.class),
                component("version", long.class));
    }

    @Test
    void entitiesExposeOnlyIntentionalPublicAndProtectedDeclaredMethods() {
        assertMethodSurface(
                AttendancePolicyEntity.class,
                method(Modifier.PUBLIC, "toDomain", AttendancePolicy.class),
                method(Modifier.PUBLIC, "effectiveFrom", LocalDate.class),
                method(Modifier.PUBLIC, "version", long.class),
                method(
                        Modifier.PUBLIC,
                        "replace",
                        void.class,
                        AttendancePolicyCommand.class,
                        long.class,
                        Instant.class),
                method(Modifier.PUBLIC, "toHistory", AttendancePolicyHistoryItem.class));
        assertMethodSurface(
                AttendanceRecordEntity.class,
                method(Modifier.PUBLIC, "toDomain", AttendanceRecord.class),
                method(Modifier.PUBLIC, "id", long.class),
                method(Modifier.PUBLIC, "setCheckOutAt", void.class, Instant.class),
                method(Modifier.PUBLIC, "workDate", LocalDate.class));
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
                method(
                        Modifier.PUBLIC,
                        "update",
                        void.class,
                        LocalDate.class,
                        String.class,
                        boolean.class,
                        long.class,
                        Instant.class),
                method(Modifier.PUBLIC, "toDomain", GlobalCalendarEvent.class),
                method(Modifier.PUBLIC, "calendarDate", LocalDate.class),
                method(Modifier.PUBLIC, "version", long.class),
                method(Modifier.PUBLIC, "toHistory", CalendarHistoryItem.class));
        assertMethodSurface(
                LeaveRequestDayEntity.class,
                method(Modifier.PUBLIC, "leaveDate", LocalDate.class),
                method(Modifier.PUBLIC, "quotaMonth", LocalDate.class),
                method(Modifier.PUBLIC, "monthlyQuotaSnapshot", int.class),
                method(Modifier.PUBLIC, "policyVersionId", long.class));
        assertMethodSurface(
                LeaveRequestDayId.class,
                method(Modifier.PUBLIC, "equals", boolean.class, Object.class),
                method(Modifier.PUBLIC, "hashCode", int.class),
                method(Modifier.PUBLIC, "leaveDate", LocalDate.class));
        assertMethodSurface(
                LeaveRequestEntity.class,
                method(Modifier.PUBLIC, "id", long.class),
                method(Modifier.PUBLIC, "internUserId", long.class),
                method(Modifier.PUBLIC, "startDate", LocalDate.class),
                method(Modifier.PUBLIC, "endDate", LocalDate.class),
                method(Modifier.PUBLIC, "reason", String.class),
                method(Modifier.PUBLIC, "status", LeaveStatus.class),
                method(Modifier.PUBLIC, "submittedAt", Instant.class),
                method(Modifier.PUBLIC, "firstCountedStartAt", Instant.class),
                method(Modifier.PUBLIC, "decidedByMentorUserId", Long.class),
                method(Modifier.PUBLIC, "decidedAt", Instant.class),
                method(Modifier.PUBLIC, "cancelledAt", Instant.class),
                method(
                        Modifier.PUBLIC,
                        "edit",
                        void.class,
                        LocalDate.class,
                        LocalDate.class,
                        String.class,
                        Instant.class),
                method(Modifier.PUBLIC, "approve", void.class, long.class, Instant.class),
                method(Modifier.PUBLIC, "reject", void.class, long.class, Instant.class),
                method(Modifier.PUBLIC, "autoReject", void.class, Instant.class),
                method(Modifier.PUBLIC, "cancel", void.class, Instant.class));
        assertMethodSurface(
                AttendanceCorrectionEntity.class,
                method(Modifier.PUBLIC, "id", long.class),
                method(Modifier.PUBLIC, "attendanceRecordId", long.class),
                method(Modifier.PUBLIC, "requestedCheckoutAt", Instant.class),
                method(Modifier.PUBLIC, "reason", String.class),
                method(Modifier.PUBLIC, "status", CorrectionStatus.class),
                method(Modifier.PUBLIC, "submittedAt", Instant.class),
                method(Modifier.PUBLIC, "submissionDeadline", Instant.class),
                method(Modifier.PUBLIC, "decisionDeadline", Instant.class),
                method(Modifier.PUBLIC, "decidedByMentorUserId", Long.class),
                method(Modifier.PUBLIC, "decidedAt", Instant.class),
                method(Modifier.PUBLIC, "lockedAt", Instant.class),
                method(
                        Modifier.PUBLIC,
                        "approve",
                        void.class,
                        long.class,
                        Instant.class,
                        String.class),
                method(
                        Modifier.PUBLIC,
                        "reject",
                        void.class,
                        long.class,
                        Instant.class,
                        String.class),
                method(Modifier.PUBLIC, "reopen", void.class, Instant.class),
                method(Modifier.PUBLIC, "autoReject", void.class, Instant.class),
                method(Modifier.PUBLIC, "lock", void.class, Instant.class));
        assertMethodSurface(
                AttendanceCorrectionEventEntity.class,
                method(Modifier.PUBLIC, "toView", com.lab.labtimesheet.feature.attendance.model.dto.CorrectionEventView.class));
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
                        String.class,
                        String.class,
                        String.class,
                        RedirectAttributes.class),
                method(
                        Modifier.PUBLIC,
                        "update",
                        String.class,
                        Principal.class,
                        long.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        RedirectAttributes.class));
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
                method(Modifier.PUBLIC, "isGlobalDayOff", boolean.class, LocalDate.class),
                method(Modifier.PUBLIC, "previewFromProvider", HolidayApiPreview.class, AttendanceActor.class, int.class),
                method(Modifier.PUBLIC, "preview", List.class, AttendanceActor.class, int.class, HolidayApiPreview.class),
                method(Modifier.PUBLIC, "importSelected", List.class, AttendanceActor.class, int.class, List.class),
                method(Modifier.PUBLIC, "history", List.class, AttendanceActor.class));
        assertMethodSurface(
                AttendancePolicyApplicationService.class,
                method(
                        Modifier.PUBLIC,
                        "schedule",
                        AttendancePolicyHistoryItem.class,
                        AttendanceActor.class,
                        AttendancePolicyCommand.class),
                method(Modifier.PUBLIC, "history", List.class, AttendanceActor.class));
        assertMethodSurface(
                LeaveApplicationService.class,
                method(Modifier.PUBLIC, "submit", com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView.class,
                        AttendanceActor.class, com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand.class),
                method(Modifier.PUBLIC, "edit", com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView.class,
                        AttendanceActor.class, long.class, com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand.class),
                method(Modifier.PUBLIC, "cancel", com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView.class,
                        AttendanceActor.class, long.class),
                method(Modifier.PUBLIC, "approve", com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView.class,
                        AttendanceActor.class, long.class),
                method(Modifier.PUBLIC, "reject", com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView.class,
                        AttendanceActor.class, long.class),
                method(Modifier.PUBLIC, "view", com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView.class,
                        AttendanceActor.class, long.class),
                method(Modifier.PUBLIC, "list", List.class, AttendanceActor.class),
                method(Modifier.PUBLIC, "expirePending", int.class, int.class));
        assertMethodSurface(
                AttendanceCorrectionApplicationService.class,
                method(Modifier.PUBLIC, "submit", com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView.class,
                        AttendanceActor.class, long.class, com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand.class),
                method(Modifier.PUBLIC, "decide", com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView.class,
                        AttendanceActor.class, long.class, com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision.class, String.class),
                method(Modifier.PUBLIC, "view", com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView.class,
                        AttendanceActor.class, long.class),
                method(Modifier.PUBLIC, "list", List.class, AttendanceActor.class),
                method(Modifier.PUBLIC, "prepareHistory", Map.class, List.class),
                method(Modifier.PUBLIC, "expire", int.class, int.class));
        assertMethodSurface(
                AttendanceDeadlineScheduler.class,
                method(Modifier.PUBLIC, "sweep", void.class));
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
