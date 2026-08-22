# Test Evidence: non-root image and PostgreSQL deployment topologies

- **Test type:** Integration
- **Requirement IDs:** `OPS-005`, `OPS-006`, `OPS-007`, `OPS-008`, `OPS-009`, `ARC-003`
- **Scenario IDs:** `AC-OPS-003`
- **Test class/method:** `src/test/js/delivery-contract.test.mjs`, Dockerfile/Compose validation, disposable readiness smoke
- **Implementation commit:** `1f2590f9654fc670e0cf93d0c4960777db2cb5f7`

## Protected behavior

The production image is a pinned multi-stage Java 25 build that runs as non-root UID/GID `10001:10001`, exposes the
readiness healthcheck, and carries the same application artifact into AMD64 and ARM64 images. Compose supports the
optional bundled PostgreSQL 18.4 profile and an external PostgreSQL 18.4 connection without changing the application
image, while preserving the named database volume and production environment boundary.

## Test method

The dependency-free delivery contract checks the pinned runtime/image, Compose profile, healthcheck, and environment
contracts. Docker BuildKit validates the Dockerfile, then builds both target architectures. The resulting AMD64 image
metadata is inspected for the non-root user, Java entrypoint, and readiness healthcheck. Two disposable Compose runs
boot the same local image: one with the bundled PostgreSQL profile and one with a separately started PostgreSQL 18.4
container reached through the external JDBC URL. Each run queries the real readiness endpoint and removes its temporary
containers, network, and volume afterward.

## Hand-derived expected result

Both image builds must complete with `BUILD SUCCESS`; the final image must use `10001:10001`, `java -jar /app/app.war`,
and `/actuator/health/readiness`. Both Compose topologies must parse and return `{"status":"UP"` from readiness over
PostgreSQL 18.4. No test publishes an image or alters a persistent database.

## RED

**Command and result**

```text
No new production behavior was introduced for this baseline row: the verified Dockerfile and Compose topology were
already present at the Iteration 3 base. A new failing test would not represent a missing implementation; this
milestone therefore records execution verification of the existing contract instead of inventing a fixture-only RED.
```

## GREEN

**Commands**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin npm run test:ui
docker build --check .
docker build --platform linux/amd64 --build-arg VCS_REF=i3-platform-validation -t labtimesheet:i3-platform-amd64 .
docker build --platform linux/arm64 --build-arg VCS_REF=i3-platform-validation -t labtimesheet:i3-platform-arm64 .
docker image inspect labtimesheet:i3-platform-amd64 --format 'image={{.Id}} user={{.Config.User}} entrypoint={{json .Config.Entrypoint}} health={{json .Config.Healthcheck.Test}}'
docker compose --env-file .env.compose.example config
docker compose --env-file .env.compose.example --profile bundled-db config
```

**Observed result**

```text
2026-08-22T14:27:22+07:00 — npm run test:ui: 8/8 passed.
Dockerfile check: Check complete, no warnings found.
AMD64 image: BUILD SUCCESS; final image digest sha256:a1f53af60f4465ac0ddca133be170defc73b4096f8324ce06cd52f3c9807f402.
ARM64 image: BUILD SUCCESS; final image digest sha256:7233c7b24e0fc7baae42c4bf063396467c460e706a788ec23228db4ddd27fadd.
Image metadata: user=10001:10001, entrypoint=["java","-jar","/app/app.war"], readiness HEALTHCHECK present.
External and bundled Compose configs: PASS.
Bundled and external disposable smoke runs: PostgreSQL 18.4 readiness returned {"status":"UP"}; cleanup completed.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests package
```

```text
2026-08-22T14:30:00+07:00 — Java 25 local production package; compiler/test-compile, WAR packaging, and Spring Boot
repackaging reported BUILD SUCCESS. Full Maven and Javadoc gates are recorded in the branch ledger after the
remaining workflow milestone is committed.
```

## External-test boundaries

This evidence does not publish to the private registry, exercise a real production database or SMTP configuration,
prove an ARM runner in Gitea, or perform an SSH deployment. Disposable smoke credentials and resources were local
test fixtures and were removed after each run.
