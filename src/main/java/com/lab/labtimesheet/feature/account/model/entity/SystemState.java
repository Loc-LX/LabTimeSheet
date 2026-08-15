package com.lab.labtimesheet.feature.account.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Durable singleton installation state used to serialize and remember first-Admin bootstrap. */
@Entity
@Table(name = "system_state")
public class SystemState {
    @Id
    @Column(name = "singleton_id")
    private short singletonId;

    @Column(nullable = false)
    private boolean initialized;

    @Column(name = "initialized_at")
    private Instant initializedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bootstrap_admin_id")
    private AppUser bootstrapAdmin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** Required by JPA; Flyway creates the singleton row. */
    protected SystemState() {
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Marks the installation initialized and retains the first Admin attribution.
     *
     * @param admin first active Admin
     * @param now server initialization timestamp
     * @throws IllegalStateException when initialization already completed
     */
    public void initialize(AppUser admin, Instant now) {
        if (initialized) {
            throw new IllegalStateException("Bootstrap is already complete");
        }
        initialized = true;
        initializedAt = now;
        bootstrapAdmin = admin;
        updatedAt = now;
    }

}
