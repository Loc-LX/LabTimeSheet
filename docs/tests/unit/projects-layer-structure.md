# Test Evidence: Project layer and JPA structure

- **Test type:** Unit
- **Requirement IDs:** `ARC-002`, `ARC-005`–`ARC-007`, `OPS-018`–`OPS-020`, `TST-001`–`TST-010`
- **Scenario IDs:** `I1-PRJ-01`–`I1-PRJ-05`
- **Test class/method:** `com.lab.labtimesheet.feature.project.repository.ProjectPersistenceStructureTest#projectPersistenceUsesTheRequiredLayerPackagesAndSpringDataJpa`
- **Implementation commits:** `25a855e`, `af0eb3c`

## Protected behavior

Project-owned production code follows the authoritative feature-first package layout, persists aggregate entities through Spring Data JPA, keeps JDBC operations out of Project business services, and does not shadow Account or Task persistence.

## Test method

Plain JUnit inspects the public Project entity, repository, and service types. It verifies their exact feature/layer packages, the entity's JPA mapping, the repository's `JpaRepository` contract, the absence of JDBC service dependencies, and the absence of foreign-table Account/Task shadow entities.

## Hand-derived expected result

The Project aggregate is under `feature.project.model.entity`, persistence under `feature.project.repository`, business logic under `feature.project.service`, the service has zero JDBC collaborators, and Account/Task persistence remains owned by those features.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectPersistenceStructureTest test
```

**Observed result**

```text
[ERROR] cannot find symbol: class ProjectUserRepository
[ERROR] cannot find symbol: class ProjectInternProfileRepository
[ERROR] cannot find symbol: class ProjectTaskRepository
[INFO] BUILD FAILURE
```

The RED was observed after removing Project-owned shadow mappings of Account and Task tables. It proves the service still required cross-feature dependencies and could not be made green by retaining forbidden repositories.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectPersistenceStructureTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.repository.ProjectPersistenceStructureTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=LayerStructureTest,ProjectPersistenceStructureTest,ProjectEntityTest test

[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Iteration 1 Javadoc retrofit verification

No behavioral RED was manufactured for documentation. The initial Project-scoped doclint run
reported 29 warnings for missing type comments, an implicit public advice constructor, and
accessor comments without main descriptions. After documenting every Project-owned production
type and declared public/protected API, the same scoped command passed:

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests -Dmaven.javadoc.failOnWarnings=true -Ddoclint=all -Dsubpackages=com.lab.labtimesheet.feature.project javadoc:javadoc

[INFO] BUILD SUCCESS
[INFO] Total time:  2.579 s
```

## External-test boundaries

This check does not prove database mappings, transaction behavior, MVC routing, or runtime authorization; those remain covered by PostgreSQL and MockMvc tests. Whole-application fail-on-warning Javadoc remains an integration responsibility after every feature owner completes the approved Iteration 1 retrofit; this evidence deliberately scopes generation to the Project-owned package.
