# Báo cáo kết quả test Iteration 2 — Lab Timesheet

- **Branch:** `work/attendance` (Iteration 2 phạm vi attendance: policy scheduling, holiday import, leave, corrections)
- **Ngày báo cáo:** 2026-08-21
- **Nguồn bằng chứng:** `duong-attendance-iteration-2-tracker.md` + các file trong `docs/tests/unit|integration|web`
- **Baseline:** PostgreSQL 18.4 Testcontainers, Spring Boot 4.1.0, Java 25.0.3, Flyway V1

## 1. Tổng quan tiến độ Iteration 2

| ID | Deliverable | Trạng thái | Commit | Kết quả test |
|---|---|---|---|---|
| I2-ATT-01 | Schedule policy version tương lai + immutable history | `DONE` | `3f617fe` | GREEN |
| I2-ATT-02 | Preview/import HolidayAPI VN + Admin chọn + provenance | `DONE` | `9be57ef` | GREEN |
| I2-ATT-03 | Materialize leave allocation + quota reservation | `DONE` | `69b54eb` | GREEN |
| I2-ATT-04 | Leave submit/approve/reject/cancel + overlap | `DONE` | `5e9e598` | GREEN |
| I2-ATT-05 | Correction submit + effective-checkout derivation | `DONE` | `4c09382` | GREEN |
| I2-ATT-06 | Mentor decide/revert + decision-window locking | `DONE` | `2ace890` | GREEN |
| I2-ATT-07 | Idempotent schedulers + request-time deadline guards | `DONE` | `169830f` | GREEN |

## 2. Chi tiết kết quả test từng hạng mục

### I2-ATT-01 — Attendance policy scheduling
- Unit: `AttendancePolicyServiceTest` — **Tests run: 7, Failures: 0, Errors: 0** (GREEN)
- Integration: `AttendancePolicySchedulingIntegrationTest` — **Tests run: 4, GREEN**
- Full suite: **228 tests pass** (evidence: `docs/tests/unit/attendance-policy-scheduling.md`, `docs/tests/integration/attendance-policy-scheduling.md`)
- RED ghi nhận: compile fail vì boundary (`AttendancePolicyService`, `SchedulePolicyCommand`, `PolicyException`) chưa tồn tại.

### I2-ATT-02 — HolidayAPI import
- Unit: `HolidayImportServiceTest` — **Tests run: 9, GREEN**
- Integration: `HolidayImportIntegrationTest` 2 + `HolidayFallbackIntegrationTest` 1 — **GREEN**
- Full suite: **243 tests pass**
- RED thật tại tầng integration (xem Issue #3).

### I2-ATT-03 — Leave materialization
- Unit: `LeaveServiceTest` — **Tests run: 8, GREEN**
- Integration: `LeaveMaterializationIntegrationTest` 4 + `LeaveConcurrencyIntegrationTest` 2 — **GREEN**
- Full suite: **257 tests pass**

### I2-ATT-04 — Leave lifecycle
- Unit: `LeaveLifecycleTest` — **Tests run: 16, GREEN**
- Integration: `LeaveWorkflowIntegrationTest` 7 — **GREEN**
- Full suite: **285 tests pass**

### I2-ATT-05 — Correction submission
- Unit: `CorrectionServiceTest` — **Tests run: 13, GREEN**
- Integration: `CorrectionPersistenceIntegrationTest` 7 — **GREEN** (sau khi sửa 3 issue, xem #5)
- Web: `InternCorrectionWebIntegrationTest` 3 — **GREEN**
- Full suite: **308 tests pass** (mốc GREEN cuối cùng của I2-ATT-05)

### I2-ATT-06 — Mentor decision
- Unit: `CorrectionWindowGuardTest` — **Tests run: 6, GREEN**
- Unit: `CorrectionDecisionServiceTest` — **Tests run: 12, GREEN**
- Integration: `CorrectionExpiryIntegrationTest` — **Tests run: 1, GREEN**
- Integration (mở rộng): `CorrectionPersistenceIntegrationTest` lên **11 tests, 0 failures** (Issue #6 đã đóng)
- Full suite: **334 tests pass**

### I2-ATT-07 — Deadline workers + request-time guards
- Unit: `AttendanceDeadlineServiceTest` — **Tests run: 7, GREEN** (mới)
- Unit: `CorrectionWindowGuardTest` — **Tests run: 6, GREEN** (cập nhật: boolean return + notification-once)
- Integration: `DeadlineGuardIntegrationTest` — **Tests run: 5, GREEN** (mới; committed-row, non-transactional, container riêng)
- Affected suite: **80 tests, 0 failures** (leave/correction workflow, persistence, web, LayerStructure)
- Attendance suite: **166 tests, 0 failures, 0 errors, 0 skipped** — `BUILD SUCCESS`
- RED ghi nhận: compile fail `cannot find symbol` cho `AttendanceNotificationClient`, `AttendanceDeadlineService`, `AttendanceScheduler` (chưa tồn tại).
- Evidence: `docs/tests/integration/deadline-guard.md`, `docs/tests/unit/attendance-deadline-worker.md`.

## 3. Các issue gặp phải trong quá trình code

### #1 — Mockito self-attaching trên JDK mới (cảnh báo, không chặn build)
- **Mô tả:** `Mockito is currently self-attaching to enable the inline-mock-maker... no longer work in future releases of the JDK` + cảnh báo dynamic Java agent.
- **Tác động:** Chỉ là warning, tests vẫn chạy.
- **Xử lý:** Giữ nguyên hiện trạng; cần thêm Mockito agent vào build trước khi nâng JDK tương lai (đã ghi chú trong nhật ký).

### #2 — Package-by-feature guard không tương thích Windows
- **Mô tả:** Guard cấu trúc package dùng path separator `/` cứng nên fail trên Windows (`\`).
- **Xử lý:** Sửa test/config cho path-separator agnostic — commit `96c2974`. Guard vẫn bảo vệ danh sách package đã duyệt.

### #3 — `@ConditionalOnMissingBean` không hoạt động khi không có client (defect thật)
- **Nơi phát hiện:** RED của `HolidayFallbackIntegrationTest` — `IllegalStateException: Failed to load ApplicationContext ... No qualifying bean of type HolidayApiClient available`.
- **Nguyên nhân:** `@ConditionalOnMissingBean` trên `@Service` không được Spring đánh giá đáng tin cậy khi không tồn tại bean nào khác.
- **Xử lý:** Chuyển fallback sang conditional `@Bean` factory trong `HolidayApiClientConfiguration` → GREEN. Đây là defect thật do integration test tìm ra, không phải test-defect.

### #4 — Mockito scaffolding trong `LeaveLifecycleTest`
- **Mô tả:** `UnfinishedStubbingException` do lồng spy construction bên trong `thenReturn`; và một `NotAMock` khi spy trên fixture không thực sự là mock; ngoài ra fixture import bị thiếu trong lần viết đầu.
- **Xử lý:** Tách spy khỏi `thenReturn`, sửa fixture import — test 16/16 GREEN (evidence `leave-lifecycle.md`).

### #5 — `CorrectionPersistenceIntegrationTest` (I2-ATT-05) có 3 lỗi test-fixture
1. Test deadline dùng record có `workDate` không khớp ngày submit → sửa fixture để khớp.
2. Assertion duplicate-persist chờ sai kiểu exception.
3. **`org.hibernate.AssertionFailure: ... null identifier`:** thử chứng minh unique constraint PostgreSQL bằng cách persist duplicate **trong cùng một transaction**. PostgreSQL abort toàn bộ transaction khi vi phạm constraint → entity lỗi có id null.
- **Xử lý:** Không persist duplicate để kiểm chứng; thay vào đó chứng minh qua outcome ổn định `ALREADY_SUBMITTED` của service (được đảm bảo bởi `uq_attendance_corrections_record` trong V1). GREEN 7/7.

### #6 — `mentorDecisionsListAllCorrectionsNewestFirstWithDerivedFlags` (I2-ATT-06, ĐÃ ĐÓNG)
- **Mô tả:** Test set clock tới `2026-08-17T09:00:01Z` trước khi gọi `decisions()`; correction đầu APPROVED lúc `2026-08-15T08:00:00Z` (deadline `2026-08-15T09:00:01Z`) → cờ `revertable` đúng là `false` (window đã qua, chưa lock), nhưng fixture kỳ vọng `true`.
- **Xử lý:** Điều chỉnh kỳ vọng đúng semantics `revertable = decided && !locked && !now.isAfter(decisionDeadline)`; trường hợp within-window được phủ riêng ở unit surface. GREEN 11/11, commit `2ace890`.

### #7 — `DeadlineGuardIntegrationTest` (I2-ATT-07) seeding trên container không transactional
- **Mô tả:** Lớp test chạy không transactional (committed-row) nên các hàng seed từ `@BeforeEach` của test trước tồn tại ở test sau → vi phạm `uq_smtp_configurations_one_active` khi activate SMTP lần 2; còn `leave.submit` cho `2026-09-05` (thứ Bảy) báo `NO_COUNTED_DAYS`.
- **Xử lý:** Seed admin/SMTP/mentor/intern **lazy một lần bằng cờ static** (vì JUnit tạo instance mới mỗi test); đổi leave thứ Bảy `2026-09-05` → thứ Năm `2026-09-03`; mỗi test correction dùng một workday riêng để tránh `ALREADY_CHECKED_IN`. GREEN 5/5.

### #8 — `@MockitoBean AttendanceNotificationClient` + fallback no-op
- **Mô tả:** Nền tảng `I2-PLAT-06` (NotificationService) chưa có, nhưng attendance cần chứng minh notification chỉ fire một lần khi transition thực sự xảy ra (ERR-004) và lỗi gửi không rollback transition (NOT-002/ERR-005).
- **Xử lý:** Attendance sở hữu port `AttendanceNotificationClient` + fallback `UnconfiguredAttendanceNotificationClient` qua `@ConditionalOnMissingBean` (mirror `HolidayApiClientConfiguration`); `@MockitoBean` trong test chèn mock đúng bean. Việc nối platform wiring ghi là dependency pending trong tracker/audit.

## 4. Kết luận & việc tiếp theo

- Iteration 2 (attendance) đã hoàn thành **7/7 hạng mục** (I2-ATT-01 → I2-ATT-07) với attendance suite **166/166 tests GREEN**.
- Các hạng mục chưa làm thuộc Iteration 2 ngoài attendance (invitations/exit, task work-logs, notifications, reports/metrics) do các branch khác sở hữu, chưa nằm trong tracker này.
- Việc tiếp theo: nối `AttendanceNotificationClient` với platform NotificationService khi `I2-PLAT-06` được cung cấp (pending dependency, đã ghi trong tracker/audit).