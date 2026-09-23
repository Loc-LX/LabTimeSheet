package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.entity.TaskComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA persistence boundary for append-only Task comments. */
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    /**
     * Loads a Task's complete comment history deterministically.
     *
     * @param taskId owning Task identifier
     * @return comments ordered by creation instant and then identifier
     */
    List<TaskComment> findAllByTaskIdOrderByCreatedAtAscIdAsc(long taskId);
}
