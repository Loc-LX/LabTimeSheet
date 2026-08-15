# Test Evidence: Production delivery baseline

- **Test type:** Integration
- **Requirement IDs:** `OPS-005`–`OPS-013`, `OPS-017`, `TST-001`, `TST-005`, `TST-009`
- **Scenario IDs:** `AC-OPS-002`, `AC-OPS-003`, `AC-OPS-004`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.BootstrapIntegrationTest.rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden`, `src/test/js/delivery-contract.test.mjs`
- **Implementation commit:** `cea4378f5699de4919c283b12371941d6742ca4b`

## Protected behavior

The production image runs as a non-root Java 25 process, exposes health probes, and accepts the same environment-backed datasource configuration with either the optional PostgreSQL 18.4 Compose sidecar or an external database. Gitea verifies every pull request and push, publishes immutable SHA plus `main` image tags only from `main`, and skips native ARM64 work unless the matching runner is explicitly available.

## Test method

The existing PostgreSQL-backed bootstrap integration test requests the liveness and readiness endpoints before initialization. A dependency-free Node contract checks the deployment files for the required runtime, Compose, trigger, permission, publication, and optional-runner boundaries. Docker and Compose validation then exercise the real build and both database topologies.

## Hand-derived expected result

An absent ARM runner must skip the ARM job without blocking AMD64 publication. Enabling the runner creates an ARM64 architecture tag and a combined manifest, while the canonical SHA and `main` tags remain valid AMD64 images when ARM is disabled. Compose must preserve PostgreSQL data in a named volume and must not require the bundled database when an external JDBC URL is supplied.

## RED

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
  PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin \
  DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
  ./mvnw '-Dtest=BootstrapIntegrationTest#rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden' test
```

**Observed result**

```text
The delivery contract ran 3 tests and failed all 3 because Dockerfile,
.gitea/workflows/verify.yml, and .gitea/workflows/container.yml did not exist.

The PostgreSQL-backed bootstrap test ran 1 test and failed because
/actuator/health/liveness returned 404 instead of 200.
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
  PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin \
  DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
  ./mvnw '-Dtest=BootstrapIntegrationTest#rootGuidesFreshInstallToBootstrapWhileOtherRoutesRemainHidden' test
```

**Observed result**

```text
Delivery contract: 3 tests, 3 passed.
Bootstrap health regression: 1 test, 1 passed against PostgreSQL 18.4.
```

## Affected suite

**Command and result**

```text
npm ci
npm run test:ui
npm run build
git diff --exit-code -- src/main/resources/static/assets/app.css src/main/resources/static/assets/icons.svg

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
  PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin \
  DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
  ./mvnw test

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
  PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin \
  ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc

docker build --check .
docker build --platform linux/amd64 --build-arg VCS_REF=validation -t labtimesheet:ci-amd64 .
docker build --platform linux/arm64 --build-arg VCS_REF=validation -t labtimesheet:ci-arm64 .
docker compose --env-file .env.compose.example config
docker compose --env-file .env.compose.example --profile bundled-db config

Frontend tests: 4 passed; generated assets remained byte-clean.
Maven: 217 tests across 49 suites, 0 failures, 0 errors, 0 skipped.
Javadoc: BUILD SUCCESS; 83 pre-existing repository-wide warnings.
Dockerfile check: passed without warnings. Both Linux architecture images built
and reported the requested platform, UID/GID 10001, and readiness HEALTHCHECK.
Both Compose configurations parsed successfully.

Real smoke tests used the AMD64 image with disposable resources. Bundled mode
started PostgreSQL 18.4 with the named volume and returned UP from liveness and
readiness on 127.0.0.1:28080. External mode used a separately started
PostgreSQL 18.4 service and returned UP from readiness on 127.0.0.1:28081.
All disposable containers, networks, and the bundled test volume were removed.

git diff --check: passed.
```

## External-test boundaries

Local validation cannot prove that the private Gitea registry credentials are configured or that an `ubuntu-latest-arm` runner is online. Repository variable `ARM64_RUNNER_AVAILABLE` is the scheduler-safe availability signal because an unavailable runner label cannot be discovered from inside a job that has not yet been scheduled.
