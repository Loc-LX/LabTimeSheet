# Test Evidence: Iteration 2 integrated exit journeys

- **Test type:** E2E
- **Requirement IDs:** `I2-PLAT-04`–`I2-PLAT-06`, `I2-TSK-01`, `I2-TSK-03`–`I2-TSK-06`, `I2-PRJ-01`–`I2-PRJ-06`, `I2-ATT-01`–`I2-ATT-06`, `I2-UI-01`–`I2-UI-05`
- **Scenario IDs:** `AC-ATT-001`, `AC-CAL-002`, `AC-CAL-004`, `AC-COR-003`, `AC-COR-004`, `AC-LEV-001`, `AC-PRJ-003`, `AC-PRJ-004`, `AC-PRJ-007`, `AC-PRJ-010`–`AC-PRJ-013`, `AC-TSK-004`, `AC-TSK-007`, `AC-TSK-011`, `AC-RPT-002`
- **Test class/method:** `Manual in-app Chromium journey: Admin, Mentor, Leader, member, removed-member, and completed-Project flows`
- **Implementation commit:** `b761290e0bb586f1a9242b52a2c669ff1f4b888f`

## Protected behavior

The integrated server-rendered application preserves policy, calendar, leave, correction, Project, Task,
notification, history, and report behavior when the real role-correct workflows are composed. Project exit batches
remain durable across cancellation or rejection; direct removal transfers unfinished work atomically; completion
closes active intervals without deleting attribution; historical viewers receive exactly the authorized read-only
surface; and an ordinary domain mutation plus in-app notification still commits when SMTP is unavailable.

## Test method

A real Java 25 Spring process at `http://127.0.0.1:18082` used the `dev` profile against disposable PostgreSQL 18.4
and Mailpit services. Six synthetic local-only users represented Admin, Mentor, former Leader, member/new Leader,
removed target, and an additional member. One Project, five Tasks, one comment, one 90-minute work log, two attendance
policy versions, one retained HolidayAPI-sourced calendar event, one cross-month leave request, and one missed-checkout
correction supplied hand-checkable data. Every business transition described below used the public browser route and
its real controller/service/persistence path.

The in-app Chromium browser exercised each role in turn. The SMTP-unavailable check deliberately retired only the
synthetic active SMTP revision in the disposable database, then submitted a new leave request through the Intern UI
and inspected the Mentor notification UI. Read-only PostgreSQL queries after the browser run corroborated the final
domain and delivery states. No production service, remote system, or real credential was used.

## Hand-derived expected result

The cross-month leave dates `31/08`, `01/09`, and `02/09` attach policy versions `1`, `2`, and `2`. Reopening the
approved correction returns it to `PENDING` while preserving `SUBMITTED`, `APPROVED`, and `REOPENED` events and a null
raw checkout. Five completed Tasks yield 100% progress; one 90-minute log yields 90 total minutes. A cancelled or
rejected exit does not reverse already committed Task transfers. Project completion closes every current membership
and leadership interval, while retained history stays visible to Admin, owning Mentor, current/former members under
the completed-Project rule. With no active SMTP revision, the new leave and in-app notification commit, while the
notification email state is `UNAVAILABLE` with zero attempts and no provider diagnostic.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test
```

**Observed result**

```text
Tests run: 444, Failures: 1, Errors: 0, Skipped: 0; BUILD FAILURE.
The final candidate gate exposed a stale CalendarController source-contract inventory after the reviewed malformed-
input repair changed typed MVC parameters to retained raw strings. All other 443 tests passed. The exact focused RED,
repair, and GREEN are recorded in docs/tests/web/iteration2-production-workflows.md.
```

No separate pre-change browser RED is claimed. The individual producer and MVC behaviors were implemented under the
companion RED/GREEN evidence files; this E2E record is the final composed-system demonstration.

## GREEN

**Command**

```text
docker run --name labtimesheet-i2-exit-pg ... postgres:18.4
docker run --name labtimesheet-i2-exit-mailpit ... axllent/mailpit
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
SPRING_PROFILES_ACTIVE=dev \
LAB_DB_URL=jdbc:postgresql://127.0.0.1:55441/labtimesheet_i2_exit \
LAB_DB_USERNAME=<local-test-user> \
LAB_DB_PASSWORD=<local-test-placeholder> \
LAB_SMTP_HOST=127.0.0.1 LAB_SMTP_PORT=1026 LAB_SERVER_PORT=18082 \
LAB_FORWARD_HEADERS_STRATEGY=none LAB_PUBLIC_ORIGIN=http://127.0.0.1:18082 \
LAB_SECURITY_MASTER_KEY=<local-test-placeholder> \
./mvnw spring-boot:run

curl --fail --silent http://127.0.0.1:18082/actuator/health
curl --fail --silent http://127.0.0.1:18082/actuator/health/liveness
curl --fail --silent http://127.0.0.1:18082/actuator/health/readiness
```

**Observed result**

```text
Java 25.0.4; Spring Boot 4.1.0; PostgreSQL 18.4; Flyway schema version 1.
Aggregate health, liveness, and readiness: UP.

Admin scheduled policy version 2 for 01/09/2026 without changing the 31/08 policy attachment, imported the prepared
calendar candidate as display-only, and opened non-secret Policy, Calendar, SMTP, and HolidayAPI History.
Intern submitted the 31/08/2026-02/09/2026 leave; Mentor approved it. The three materialized days retained policy IDs
1, 2, and 2. Intern submitted a missed-checkout correction; Mentor approved and reopened it. The UI retained the
ordered SUBMITTED/APPROVED/REOPENED history and returned the correction to PENDING.

Leader reassigned an unfinished Task while preserving creator/log/comment facts. A 90-minute work log and one comment
remained in history. Exit batches were committed, then observed to survive one cancellation and one rejection. Mentor
approved a zero-unfinished ordinary-member removal, directly removed another ordinary member with automatic transfer,
changed Leader without moving Tasks, and approved the former Leader only after the new Leader transferred the three
remaining unfinished Tasks. The one pending invitation was superseded by Mentor direct-add. After all five Tasks were
DONE, Mentor completed the Project.

The completed Project History showed invitation SUPERSEDED and exit states CANCELLED, APPROVED, APPROVED, and REJECTED,
plus retained membership/leadership actor IDs, five Tasks, one comment, and the 90-minute log. Former Leader, owning
Mentor, and Admin each received the authorized read-only completed-history view with no mutation controls. Attendance
and Project reports showed role-reduced pickers, 5/5 DONE, 100%, and 90 minutes; the Intern report exposed self-only
attendance scope.

After the synthetic SMTP revision was retired, Intern leave #2 persisted as PENDING and the Mentor notification inbox
showed the new LEAVE_SUBMITTED event. PostgreSQL corroboration reported notification #63 as UNAVAILABLE, attempts 0,
email_last_error null. The existing Mailpit inbox remained available; no retroactive-send action was exposed.

Final PostgreSQL corroboration: Project 1 COMPLETED; Tasks 5/5 DONE; total work 90 minutes; all active Project
membership/leadership intervals closed; correction events SUBMITTED/APPROVED/REOPENED; leave 1 APPROVED and leave 2
PENDING. The Spring process then completed graceful Tomcat/JPA/Hikari shutdown with Maven `BUILD SUCCESS`.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test

PostgreSQL 18.4 where applicable; Tests run: 444, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.

npm run build && npm run test:ui
Node 24/npm 11; frontend build succeeded; UI tests 7/7 passed.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests compile
BUILD SUCCESS.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS with 100 warnings and no errors.
```

## External-test boundaries

The browser run proves the listed local Chromium workflows against the real Java/PostgreSQL application and synthetic
data. It does not contact HolidayAPI or a real SMTP server, exercise multi-node session delivery, measure frame-level
theme paint, or qualify mobile/tablet layouts. The retained browser became unreachable when the local Java process was
interrupted after the journeys; the later connection was not bypassed when browser URL policy rejected localhost
reload. Fresh process health and read-only PostgreSQL checks on the same exact commit corroborated the persisted state,
but no post-interruption browser-console or viewport measurement is claimed. Automated MVC/Node accessibility tests
separately protect keyboard, focus, redaction, reduced-motion, and adjacent-table contracts.
