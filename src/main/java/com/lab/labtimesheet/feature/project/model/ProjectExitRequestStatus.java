package com.lab.labtimesheet.feature.project.model;

/**
 * Persisted lifecycle of a Project membership-exit request.
 */
// Trạng thái xử lý yêu cầu exit, từ chờ Mentor quyết định đến các kết quả cuối.
public enum ProjectExitRequestStatus {
    /** Request awaits redistribution and owning-Mentor decision. */
    PENDING,
    /** Owning Mentor approved and the membership interval was closed. */
    APPROVED,
    /** Owning Mentor rejected the request without closing membership. */
    REJECTED,
    /** Original requester cancelled the request. */
    CANCELLED,
    /** Project completion superseded the request. */
    SUPERSEDED
}
