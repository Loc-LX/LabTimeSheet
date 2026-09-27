package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.entity.TaskStatusTransition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence queries for append-only Task status history. */
public interface TaskStatusTransitionRepository extends JpaRepository<TaskStatusTransition, Long> {

    /** Finds the most recent block for a Task, using its insertion id to break equal-time ties. */
    Optional<TaskStatusTransition> findFirstByTaskIdAndToStatusOrderByOccurredAtDescIdDesc(
            long taskId, TaskStatus toStatus);
}
