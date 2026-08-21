package com.lab.labtimesheet.feature.task.service;

/**
 * Result of one atomic Task transfer operation.
 *
 * @param transferredTaskCount number of unfinished Tasks reassigned in the transaction
 * @param recipientMembershipId recipient membership supplied by the authorized Project caller
 */
public record TaskTransferResult(long transferredTaskCount, long recipientMembershipId) {}
