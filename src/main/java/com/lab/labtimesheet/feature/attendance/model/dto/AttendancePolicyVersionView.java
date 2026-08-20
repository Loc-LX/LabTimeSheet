package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;

/**
 * One rendered timeline row pairing the immutable policy version with its current
 * optimistic version so future versions can be replaced safely.
 *
 * @param policy effective-dated attendance rules
 * @param version optimistic version required by replacement requests
 */
public record AttendancePolicyVersionView(AttendancePolicy policy, long version) {}