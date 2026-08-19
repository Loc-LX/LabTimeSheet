package com.lab.labtimesheet.feature.project.model;

/** Vòng đời aggregate Project; trạng thái Completed là kết thúc và chỉ đọc. */
public enum ProjectStatus {
    /** Trạng thái chuẩn bị, cho phép thay đổi thành viên, Leader và định nghĩa Task. */
    PLANNED,
    /** Trạng thái thực thi, cho phép công việc của Project diễn ra. */
    ACTIVE,
    /** Trạng thái kết thúc chỉ đọc, vẫn giữ khả năng xem thành viên và nhiệm kỳ Leader lịch sử. */
    COMPLETED
}
