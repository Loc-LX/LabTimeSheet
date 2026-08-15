package com.lab.labtimesheet.feature.account.model;

/** Durable authentication lifecycle of a global account. */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    LOCKED,
    DEACTIVATED
}
