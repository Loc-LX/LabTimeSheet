package com.lab.labtimesheet.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

class AttendanceLayerStructureTest {

    @Test
    void attendanceUsesAuthoritativeLayerPackagesWithoutLegacyFeaturePackage() throws Exception {
        for (String className : List.of(
                "com.lab.labtimesheet.feature.attendance.controller.AttendanceController",
                "com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem",
                "com.lab.labtimesheet.feature.attendance.exception.AttendanceException",
                "com.lab.labtimesheet.feature.calendar.model.AttendancePolicy",
                "com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService",
                "com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity",
                "com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository",
                "com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService")) {
            assertThat(Class.forName(className)).isNotNull();
        }

        assertThatThrownBy(() -> Class.forName("com.lab.labtimesheet.attendance.AttendanceService"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.lab.labtimesheet.controller.AttendanceController"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void attendanceQueriesUseSpringDataJpaRatherThanJdbcTemplate() throws Exception {
        Class<?> queryRepository = Class.forName(
                "com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository");
        assertThat(Repository.class).isAssignableFrom(queryRepository);

        for (String serviceName : List.of(
                "com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService",
                "com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService")) {
            assertThat(Class.forName(serviceName).getDeclaredFields())
                    .allSatisfy(field -> assertThat(field.getType()).isNotEqualTo(JdbcTemplate.class));
        }
    }

    @Test
    void attendanceDoesNotMapOrExposeAccountFeatureTables() {
        for (String className : List.of(
                "com.lab.labtimesheet.feature.attendance.model.entity.AppUserEntity",
                "com.lab.labtimesheet.feature.attendance.model.entity.InternProfileEntity",
                "com.lab.labtimesheet.feature.attendance.repository.AppUserRepository",
                "com.lab.labtimesheet.feature.attendance.repository.InternProfileRepository")) {
            assertThatThrownBy(() -> Class.forName(className))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }
}
