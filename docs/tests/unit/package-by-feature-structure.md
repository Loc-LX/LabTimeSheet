# Test Evidence: Package-by-feature structure

- **Test type:** Unit
- **Requirement IDs:** `ARC-001–ARC-008`
- **Scenario IDs:** No direct acceptance-scenario mapping (architecture regression)
- **Test class/method:** `com.lab.labtimesheet.config.LayerStructureTest.applicationUsesOnlyApprovedPackageByFeatureStructure`
- **Implementation commit:** `1235204bf1298599264a07943ca1167432556bd2`

## Protected behavior

The Spring Boot application class remains in the root package, shared wiring remains in `config`, and business code uses only the approved feature and feature-layer packages. Legacy feature-first placeholders, global business layers, and cross-feature repository/entity imports are rejected.

## Test method

A no-dependency JUnit test inspects the production source tree. It checks the root directories, permits the complete seven-feature vocabulary for branch integration, limits nested packages to the approved feature layers, and scans Java imports for persistence leakage across features.

## Hand-derived expected result

The platform branch has only `config` and `feature` below `com.lab.labtimesheet`; its present features are a nonempty subset of account, integration, project, task, attendance, notification, and reporting. A feature may call another feature's public service/DTO API but must not import another feature's repository or entity.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -Dtest=LayerStructureTest test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
actual directories included exception, controller, projects, configuration,
repository, service, model, accounts, config, attendance, dto, reporting,
and notifications; expected feature and config
BUILD FAILURE
```

The failure exposed both the superseded global-layer worktree and the committed legacy `ModuleBoundary` package placeholders before the corrective move.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -Dtest=LayerStructureTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This source-tree regression protects package naming and import direction. It does not prove runtime authorization, database transaction behavior, browser flows, containerization, CI, or deployment.
