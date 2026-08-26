package com.lab.labtimesheet.feature.task.repository;

import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence boundary for append-only initial and correction Remaining effort forecasts. */
public interface TaskRemainingEffortForecastRepository extends JpaRepository<TaskRemainingEffortForecast, Long> {

    /**
     * Reads retained forecast history for one scoped Task in deterministic assignment order.
     *
     * @param projectId owning Project identifier
     * @param taskId Task identifier
     * @return immutable forecast rows ordered by assignment and creation time
     */
    List<TaskRemainingEffortForecast> findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
            long projectId, long taskId);

    /** Finds one retained forecast within its Project/Task scope. */
    Optional<TaskRemainingEffortForecast> findByIdAndProjectIdAndTaskId(
            long id, long projectId, long taskId);

    /** Returns whether a predecessor already has its single linear successor. */
    boolean existsBySupersedesForecastId(long forecastId);
}
