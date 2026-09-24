package com.lab.labtimesheet.feature.identity.model;

/** Durable authentication lifecycle of a global account. */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    LOCKED,
    DEACTIVATED
}
