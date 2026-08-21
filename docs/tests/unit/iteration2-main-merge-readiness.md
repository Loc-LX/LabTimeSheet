# Test Evidence: Iteration 2 main-merge documentation readiness

- **Test type:** Unit
- **Requirement IDs:** `PROJECT_PLAN §9; Iteration 2 orchestration final-audit artifact requirement`
- **Scenario IDs:** `N/A — local integration governance contract`
- **Test class/method:** `Shell merge-readiness contract`
- **Implementation commit:** `fdb4871894fa37af5c5eec863c4ccb4d67c99c8a`
- **Merged-main verification tree:** `600e5fda478f1893d386f32fbf7db3ba19228cff`

## Protected behavior

The reviewed Iteration 2 candidate must publish all six tracked human-readable Git ledgers, describe Iteration 2 as current in the README, and mark the plan as actively awaiting the authorized local `main` merge. Missing ledgers or stale Iteration 1/Iteration 2 status text blocks integration.

## Test method

Run one repository-local shell contract that checks the six exact tracked paths plus the two stale README markers and the Iteration 2 overview row. This is the narrowest executable check for a documentation-only merge-readiness defect.

## Hand-derived expected result

Before the repair, six paths are missing and three stale markers fail. After the repair, every file exists, neither stale README marker remains, and the overview contains `IN_PROGRESS` with `Pending authorized local main merge`.

## RED

**Command**

```text
rtk proxy sh -c 'failed=0
for file in docs/iterations/iteration-2/git-ledgers/work-platform.md docs/iterations/iteration-2/git-ledgers/work-tasks.md docs/iterations/iteration-2/git-ledgers/work-projects.md docs/iterations/iteration-2/git-ledgers/work-attendance.md docs/iterations/iteration-2/git-ledgers/work-reports-ui.md docs/iterations/iteration-2/git-ledgers/orchestrator-integration.md; do
  if [ ! -f "$file" ]; then echo "missing: $file"; failed=1; fi
done
if rg -q "^## Iteration 1: working now$" README.md; then echo "stale: README still presents Iteration 1 as current"; failed=1; fi
if rg -q "^- Iteration 2:" README.md; then echo "stale: README still defers Iteration 2"; failed=1; fi
if ! rg -q "^\\| 2 .*IN_PROGRESS.*Pending authorized local main merge" .agents/PROJECT_PLAN.md; then echo "stale: Iteration 2 overview is not merge-ready"; failed=1; fi
exit "$failed"'
```

**Observed result**

```text
Exit 1. The command reported all six ledger paths missing, the README's Iteration 1-current and Iteration 2-deferred text, and the plan's non-merge-ready Iteration 2 overview.
```

## GREEN

**Command**

```text
Same exact shell contract as RED.
```

**Observed result**

```text
Exit 0 with no output. All six files exist, both stale README markers are absent, and the plan row is merge-ready.
```

## Affected suite

**Command and result**

```text
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test
PostgreSQL 18.4; Tests run: 444, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS in 04:44.

The first sandboxed attempt enumerated 444 tests but ended with 371 cascading context errors because Byte Buddy could not self-attach to the sandboxed JVM. The identical host-level command above attached successfully and passed without a source/configuration change.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test
PostgreSQL 18.4; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. Flyway verified schema version 1, 23 application tables, and 56 foreign keys.

rtk npm run build
Tailwind CSS 4.3.3 build passed.

rtk npm run test:ui
UI tests: 7 passed, 0 failed.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests compile
BUILD SUCCESS.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS.

Requirement parity: 260 rows/260 unique IDs in each of the authoritative, explained, simple, and generated-SRS catalogues; generated SRS use cases: 14. Iteration 2 tracker rows: 30 DONE.

Real-process smoke: a disposable `postgres:18.4` container on `127.0.0.1:55442` migrated from empty schema to version 1. A Java 25 `spring-boot:run` process using the exact `.env.example` variable set started on `127.0.0.1:18083`; aggregate health, liveness, and readiness each returned `UP`. Ctrl-C produced graceful Tomcat/JPA/Hikari shutdown. The exact disposable container auto-removed and ports 55442/18083 were clear.
```

## External-test boundaries

The shell contract proves only tracked artifact presence and current handoff text. Producer ancestry, code behavior, PostgreSQL/Flyway behavior, browser workflows, remote movement, and the final merged-`main` SHA require their separate Git and exit-gate checks.

## Local main integration

- **Fresh fetched base:** `origin/main` = `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- **Reviewed candidate:** `f334f13594de49f4b34318d8a3e8bc0556a8063d` (`APPROVE`, 0 Critical/Important)
- **Normal local merge:** `c4f039663f86370b865df036e1756338529e47c9`
- **Merge parents:** `58a087b118cc955748d7df1aa47d2bbc3ca0371b`, `f334f13594de49f4b34318d8a3e8bc0556a8063d`
- **Protected root file at merge:** `.DS_Store` SHA-256 `bf6f1f27ea596b8a0dfd8795ccc9ba41629abb79e75b536d2964146e8815aee8`, 10,244 bytes; the merge left it unchanged and unstaged
- **Remote mutation:** none; no push
- **Exact merged-main gate:** passed at `600e5fda478f1893d386f32fbf7db3ba19228cff`

**Post-merge documentation command**

```text
rtk proxy sh -c 'failed=0
for file in docs/iterations/iteration-2/git-ledgers/work-platform.md docs/iterations/iteration-2/git-ledgers/work-tasks.md docs/iterations/iteration-2/git-ledgers/work-projects.md docs/iterations/iteration-2/git-ledgers/work-attendance.md docs/iterations/iteration-2/git-ledgers/work-reports-ui.md docs/iterations/iteration-2/git-ledgers/orchestrator-integration.md; do [ -f "$file" ] || failed=1; done
rg -q "^\\| 2 .*DONE.*c4f039663f86370b865df036e1756338529e47c9" .agents/PROJECT_PLAN.md || failed=1
rg -q "c4f039663f86370b865df036e1756338529e47c9" docs/iterations/iteration-2/git-ledgers/orchestrator-integration.md || failed=1
rg -q "No push|no push" docs/iterations/iteration-2/git-ledgers/orchestrator-integration.md || failed=1
exit "$failed"'
```

**Observed result**

```text
Exit 0 with no output. The tracked plan and ledgers identify the exact local merge and preserve the no-push boundary.
```

## Exact merged-main exit gate

All commands below ran from exact local `main` tree `600e5fda478f1893d386f32fbf7db3ba19228cff`, whose parent is merge commit `c4f039663f86370b865df036e1756338529e47c9`.

**Java/PostgreSQL suite**

```text
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test
PostgreSQL 18.4; Tests run: 444, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS in 04:55.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test
PostgreSQL 18.4; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. The schema contract remains 23 application tables and 56 foreign keys.
```

**Frontend, compile, and Javadoc**

```text
rtk proxy env PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin npm ci
36 packages installed; exit 0.

rtk proxy env PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin npm run build
Tailwind CSS 4.3.3, local Lucide, and pinned Chart.js assets built; exit 0.

rtk proxy env PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin npm run test:ui
Tests run: 7, Passed: 7, Failed: 0.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests compile
BUILD SUCCESS.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS.
```

The first root `rtk npm run build` attempt used the pre-merge `node_modules` under the shell's Node 26 and failed because merged Chart.js was absent. The pinned Node 24 `npm ci` refreshed only ignored dependencies; the rerun above passed without source or lockfile changes.

**Catalogue and tracker parity**

```text
Pinned Node 24 catalogue script: authoritative 260/260 unique; explained 260/260 unique; simple 260/260 unique; generated SRS 260/260 unique; SRS use cases 14; exit 0.

rtk proxy awk '/^\| I2-/ && /DONE/{done++} END{print "Iteration 2 DONE rows:", done+0; exit(done==30?0:1)}' .agents/PROJECT_PLAN.md
Iteration 2 DONE rows: 30; exit 0.
```

**Real-process smoke**

```text
rtk docker run --rm -d --name labtimesheet-i2-merge-main-postgres -e POSTGRES_DB=labtimesheet_merge_main -e POSTGRES_USER=labtimesheet -e POSTGRES_PASSWORD=merge-only-password -p 55443:5432 --health-cmd 'pg_isready -U labtimesheet -d labtimesheet_merge_main' --health-interval 1s --health-timeout 5s --health-retries 30 postgres:18.4
Container became healthy.

rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25 SPRING_PROFILES_ACTIVE=dev LAB_SERVER_PORT=18084 LAB_FORWARD_HEADERS_STRATEGY=NONE LAB_DB_URL=jdbc:postgresql://127.0.0.1:55443/labtimesheet_merge_main LAB_DB_USERNAME=labtimesheet LAB_DB_PASSWORD=merge-only-password LAB_SMTP_HOST=127.0.0.1 LAB_SMTP_PORT=1025 LAB_SECURITY_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= LAB_PUBLIC_ORIGIN=http://127.0.0.1:18084 ./mvnw spring-boot:run
Java 25 started on port 18084; Flyway migrated an empty PostgreSQL 18.4 database to version 1.

rtk proxy curl --fail --silent --show-error http://127.0.0.1:18084/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}

rtk proxy curl --fail --silent --show-error http://127.0.0.1:18084/actuator/health/liveness
{"status":"UP"}

rtk proxy curl --fail --silent --show-error http://127.0.0.1:18084/actuator/health/readiness
{"status":"UP"}
```

Ctrl-C produced graceful Tomcat, JPA, and Hikari shutdown. `rtk docker stop labtimesheet-i2-merge-main-postgres` stopped and auto-removed the exact container; ports 55443 and 18084 were clear.

## Final workspace boundary

The index remained clean throughout verification. macOS later updated the already-uncommitted root `.DS_Store` and `docs/.DS_Store`; immediately before this evidence update they were 10,244 bytes/SHA-256 `89f273327946f1a0035ae24b2f67ad1642fc81b706f2a153d98b8b282d598069` and 6,148 bytes/SHA-256 `0184fce87d9491b8d655bfcad34657c033b07bfe6c67c758c8d1f02a6b8e0175`. Both remain unstaged and deliberately untouched. No branch/worktree was deleted and no push or other remote mutation occurred.
