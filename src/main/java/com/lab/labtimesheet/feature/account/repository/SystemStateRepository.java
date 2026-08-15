package com.lab.labtimesheet.feature.account.repository;

import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.entity.SystemState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Persistence boundary for the single durable installation-state row. */
public interface SystemStateRepository extends JpaRepository<SystemState, Short> {
    /**
     * Locks the singleton row so concurrent bootstrap attempts cannot both create a first Admin.
     *
     * @return locked installation state
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemState s where s.singletonId = 1")
    Optional<SystemState> findSingletonForUpdate();
}
