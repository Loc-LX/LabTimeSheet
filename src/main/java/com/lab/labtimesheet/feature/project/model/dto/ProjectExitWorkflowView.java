package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Render-ready pending-exit warning and capability snapshot.
 *
 * <p>These values remain informational. Transfer and Mentor-decision services repeat every
 * authorization, membership, leadership, Task-count, and state check under their mutation locks.</p>
 *
 * @param requestId pending request identifier
 * @param targetMembershipId target membership interval
 * @param targetName current display name for the target
 * @param replacementRequired whether the target is still current Leader
 * @param unfinishedTaskCount current unfinished Task count
 * @param readyForMentorDecision whether the current snapshot satisfies approval guards
 * @param canOpenTransfer whether the authenticated current Leader may open transfer controls
 */
public record ProjectExitWorkflowView(
        long requestId,
        long targetMembershipId,
        String targetName,
        boolean replacementRequired,
        long unfinishedTaskCount,
        boolean readyForMentorDecision,
        boolean canOpenTransfer) {
}
