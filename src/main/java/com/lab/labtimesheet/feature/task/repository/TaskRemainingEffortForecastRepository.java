package com.lab.labtimesheet.feature.task.repository;

import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence boundary for immutable initial Remaining effort forecasts. */
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
}
