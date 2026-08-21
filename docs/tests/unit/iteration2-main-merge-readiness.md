# Test Evidence: Iteration 2 main-merge documentation readiness

- **Test type:** Unit
- **Requirement IDs:** `PROJECT_PLAN §9; Iteration 2 orchestration final-audit artifact requirement`
- **Scenario IDs:** `N/A — local integration governance contract`
- **Test class/method:** `Shell merge-readiness contract`
- **Implementation commit:** `fdb4871894fa37af5c5eec863c4ccb4d67c99c8a`

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
