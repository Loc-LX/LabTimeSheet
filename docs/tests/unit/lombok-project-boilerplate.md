# Test Evidence: Targeted Project Lombok boilerplate

- **Test type:** Temporary source audit (removed after GREEN)
- **Requirement IDs:** `ARC-002`, `ARC-005`, `ARC-006`, `TST-001`
- **Scenario IDs:** `AC-TST-001`
- **Test class/method:** N/A; the temporary source audit was removed after its RED/GREEN cycle
- **Implementation commit:** `e5639c1`

## Protected behavior

Project Spring components use targeted required-argument constructor generation only when their
constructors assign required final dependencies. Project JPA entities use only protected no-arg
constructor generation. Records remain records, and entity identity, lazy associations, explicit
domain accessors, aggregate constructors, and mutation methods do not gain broad generated APIs.

## Temporary RED/GREEN method

The temporary source-contract test inspected only `feature.project` production sources. It required
`@RequiredArgsConstructor` on the three injection-only components, removal of the stateless advice's
handwritten no-arg constructor, and protected `@NoArgsConstructor` on the three JPA entities. It also
rejected broad entity Lombok annotations, confirmed representative explicit domain APIs remained,
and verified every Project immutable DTO/value type remained a Java record. It was deleted after
preserving the historical RED/GREEN below because exact imports, annotation spelling, and source
substrings are implementation details rather than a durable public contract.

## Hand-derived expected result

Seven handwritten constructors are mechanical and eligible for removal: three dependency-assignment
constructors, one empty advice constructor, and three empty protected JPA constructors. The three
entity domain constructors, all aggregate mutation methods, defensive-copy accessors, derived
membership/leadership accessors, validation constructors, exception constructors, and all thirteen
records must remain explicit or remain records because they carry behavior or preserve the existing
API shape.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectLombokBoilerplateTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.repository.ProjectLombokBoilerplateTest
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
ProjectLombokBoilerplateTest.eligibleConstructorsUseTargetedLombokWithoutChangingDomainApis
expected ProjectController.java to contain import lombok.RequiredArgsConstructor; and
@RequiredArgsConstructor, but neither was present and the handwritten assignment-only constructor
remained.
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectLombokBoilerplateTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.repository.ProjectLombokBoilerplateTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests compile
[INFO] BUILD SUCCESS

./mvnw -Dtest=ProjectEntityTest,ProjectPersistenceStructureTest,ProjectTaskMutationContextTest test
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

./mvnw -Dtest=ProjectControllerTest test
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectServiceIntegrationTest test
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

./mvnw -Dtest='Project*Test' test
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

./mvnw -DskipTests -Dmaven.javadoc.failOnWarnings=true -Ddoclint=all \
  -Dsubpackages=com.lab.labtimesheet.feature.project javadoc:javadoc
[INFO] BUILD SUCCESS

git diff --check
(no output; exit 0)
```

## External-test boundaries

The removed source audit did not prove Lombok annotation processing, Spring constructor injection,
Hibernate materialization, PostgreSQL mappings, Thymeleaf behavior, Project authorization, locking,
or aggregate lifecycle rules. Those durable boundaries are covered by the compile, scoped
Javadoc/doclint, Project unit/web, and PostgreSQL 18.4 integration gates above. The first sandboxed
unit-suite attempt could not attach Mockito's Byte Buddy agent; the unchanged command passed after
approved execution outside that sandbox. Browser E2E behavior remains outside this unit milestone.
