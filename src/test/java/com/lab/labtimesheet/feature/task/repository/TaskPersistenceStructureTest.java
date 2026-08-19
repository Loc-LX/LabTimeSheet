package com.lab.labtimesheet.feature.task.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.service.TaskService;
import jakarta.persistence.Entity;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.jdbc.core.simple.JdbcClient;

class TaskPersistenceStructureTest {

    @Test
    void taskPersistenceUsesJpaEntitiesAndSpringDataRepositories() {
        assertThat(Task.class).hasAnnotation(Entity.class);
        assertThat(TaskComment.class).hasAnnotation(Entity.class);
        assertThat(JpaRepository.class).isAssignableFrom(TaskRepository.class);
        assertThat(JpaRepository.class).isAssignableFrom(TaskCommentRepository.class);
    }

    @Test
    void taskServiceUsesTaskRepositoriesInsteadOfDirectJdbcAccess() {
        var constructorTypes = Arrays.stream(TaskService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .toList();

        assertThat(constructorTypes)
                .contains(TaskRepository.class, TaskCommentRepository.class)
                .doesNotContain(JdbcClient.class);
    }

    @Test
    void taskMutationLookupUsesAPessimisticWriteLock() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod(
                "findLockedByIdAndProjectIdAndDeletedAtIsNull", long.class, long.class);

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    /** [I2-PRJ-04] Luồng transfer phải khóa các Task chưa hoàn thành trước khi đổi assignee. */
    @Test
    void unfinishedTransferLookupUsesAPessimisticWriteLock() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod(
                "findLockedUnfinishedByProjectIdAndAssigneeMembershipId", long.class, long.class);

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    /** [I2-PRJ-05] Repository phải có truy vấn đếm Task chưa DONE cho guard hoàn tất Project. */
    @Test
    void unfinishedTaskCountLookupIsPresent() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod("countUnfinishedByProjectId", long.class);

        assertThat(method).isNotNull();
    }

    @Test
    void taskBusinessCodeContainsNoDirectJdbcOrSqlImports() throws IOException {
        Path taskSource = Path.of("src/main/java/com/lab/labtimesheet/feature/task");
        try (var sources = Files.walk(taskSource)) {
            var directSqlSources = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("import org.springframework.jdbc")
                                    || source.contains("import java.sql");
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect " + path, exception);
                        }
                    })
                    .toList();

            assertThat(directSqlSources).isEmpty();
        }
    }
}
