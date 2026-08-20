# Test Evidence: Attendance targeted Lombok boilerplate retrofit

- **Test type:** Unit compiled-contract audit
- **Requirement IDs:** `ATT-001`–`ATT-012`, `CAL-001`, `CAL-006`–`CAL-009`
- **Scenario IDs:** `AC-ATT-001`–`AC-ATT-005`, `AC-CAL-003`, `AC-CAL-004`
- **Test class/method:**
  `com.lab.labtimesheet.feature.attendance.AttendanceLombokBoilerplateTest#generatedConstructorsPreserveParameterListsAndVisibility`,
  `com.lab.labtimesheet.feature.attendance.AttendanceLombokBoilerplateTest#immutableModelsRemainRecordsWithTheirComponentContracts`,
  `com.lab.labtimesheet.feature.attendance.AttendanceLombokBoilerplateTest#entitiesExposeOnlyIntentionalPublicAndProtectedDeclaredMethods`,
  `com.lab.labtimesheet.feature.attendance.AttendanceLombokBoilerplateTest#componentsExposeOnlyIntentionalPublicAndProtectedDeclaredMethods`
- **Implementation commit:** `82ad8202fd31f77db8c3932a902dba07cee70894`

## Protected behavior

Attendance uses the installed Lombok processor only for mechanical constructors while preserving the compiled API:
package-level Spring injection, protected JPA construction, immutable record components, domain constructors and
mutations, raw punch and attached-policy history rules, composite-key identity, and existing public method names.

## Test method

Reflection inspects compiled `feature.attendance` classes rather than source spelling. It verifies every constructor's
parameter order and modifier, every immutable model's record components, and the exact public/protected declared method
surface of each Attendance entity. The entity surface prevents generated bean getters/setters or entity
`equals`/`hashCode`/`toString` widening while explicitly retaining `AttendanceRecordEntity#setCheckOutAt` and the
`LeaveRequestDayId` identity methods. Exact controller and service surfaces likewise prevent Lombok from exposing
collaborator getters/setters or generated `equals`/`hashCode`/`toString` methods.

## Hand-derived expected result

Five injection-only components expose only their package-scoped dependency constructors. Six JPA/embeddable types
retain protected no-argument construction alongside their intentional domain constructors, and the stateless domain
service remains package-scoped. Seven immutable models remain records with the same component order and types. Entity
method surfaces contain only intentional domain conversion/access/mutation methods; only the composite key owns
`equals` and `hashCode`, and no Attendance entity declares `toString`. Both controllers and all four Attendance
services expose only their existing route or application/domain operations, never their injected collaborators.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceLombokBoilerplateTest test
```

**Observed result**

```text
Tests run: 3, Failures: 2, Errors: 0, Skipped: 0
The initial source audit first failed on AttendanceController because its mechanical dependency constructor remained,
and on AttendancePolicyEntity because its mechanical protected JPA constructor remained. The immutable-record and
business-method retention guard passed. After this RED established the retrofit gap, the permanent regression was
replaced with compiled reflection/API checks so formatting or annotation spelling cannot affect the result.
BUILD FAILURE
Process exited 1.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceLombokBoilerplateTest test
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='*Attendance*Test' test
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL 18.4 started and Flyway applied V1 for the persistence and concurrency contexts.
BUILD SUCCESS
Process exited 0.
```

## Extension (I2-ATT-03)

`69b54eb` extended the compiled-contract audit to the leave stack: the
`InternLeaveController` (form/submit) and `LeaveService` (submit/overview)
method surfaces were added, `LeaveRequestDayEntity` gained its public 4-arg
constructor and getter surface, `LeaveRequestDayId` gained `leaveDate()`, and
`LeaveRequestEntity` gained the `PENDING` 7-arg constructor plus the public
static `pending` factory and its getter surface. All four test methods still
pass unchanged at the full-suite run recorded in `docs/tests/unit/leave-service.md`.

## Extension (I2-ATT-04)

`5e9e598` extended the compiled-contract audit to the leave lifecycle:
`LeaveRequestEntity` gained its decision/cancellation mutators
(`approve`/`reject`/`cancel`/`updateRange`) and getters
(`decidedByMentorUserId`/`decidedAt`/`decisionNote`/`cancelledAt`),
`LeaveService` gained `decide`/`cancel`/`edit`/`decisions`, the
`InternLeaveController` gained `cancel`/`edit`, and the new
`MentorLeaveController` surface (form/approve/reject) is asserted. All four
test methods still pass unchanged at the full-suite run recorded in
`docs/tests/unit/leave-lifecycle.md`.

## Extension (I2-ATT-05)

`4c09382` extended the compiled-contract audit to the correction stack:
`AttendanceApplicationService` gained the injected
`AttendanceCorrectionRepository` (8-param constructor),
`AttendanceRecordEntity` gained `id()`/`checkInAt()`/`checkOutAt()`,
`AttendanceHistoryItem` gained its `effectiveCheckOutAt` component, the new
`InternCorrectionController` (form/submit) and `CorrectionService`
(submit/overview) surfaces are asserted, and the new correction entities and
DTOs are covered: `AttendanceCorrectionEntity` (protected JPA + 6-arg public
constructor, `approve` mutator, and decision getters), `AttendanceCorrectionEventEntity`
(protected JPA + 7-arg public constructor and getter surface), and the
`CorrectionSubmission` (9 components), `CorrectionSubmissionCommand`
(3 components), and `CorrectionsOverview` (2 components) records. All four test
methods still pass unchanged at the full-suite run recorded in
`docs/tests/unit/correction-submission.md`.

## External-test boundaries

The reflection audit does not replace Spring/JPA bootstrapping, MVC property access, PostgreSQL persistence, or
Javadoc/doclint. Those checks remain affected verification. No application behavior or public API is intentionally
changed by this retrofit.

Additional verification on the same source tree:

```text
./mvnw -DskipTests compile
BUILD SUCCESS

./mvnw -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/attendance-javadoc-classpath.txt
javadoc -quiet -Xdoclint:all -d target/attendance-javadocs -classpath "target/classes:$(tr -d '\n' < target/attendance-javadoc-classpath.txt)" -sourcepath src/main/java -subpackages com.lab.labtimesheet.feature.attendance
Process exited 0. The source frontend reported seven generated-constructor missing-comment warnings because it does not
expand Lombok constructors; repository policy exempts generated trivial constructors from duplicate Javadoc.
```
