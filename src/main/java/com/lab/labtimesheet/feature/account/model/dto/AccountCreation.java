package com.lab.labtimesheet.feature.account.model.dto;

/**
 * Result of creating a pending account and attempting its immediate activation delivery.
 *
 * @param userId created account identifier
 * @param deliverySucceeded whether the initial activation email was accepted by the configured SMTP boundary
 */
public record AccountCreation(long userId, boolean deliverySucceeded) {
}
