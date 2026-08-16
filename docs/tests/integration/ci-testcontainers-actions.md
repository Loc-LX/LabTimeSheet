# Test Evidence: Gitea Testcontainers and container workflow gates

- **Test type:** Integration
- **Requirement IDs:** `OPS-011`, `OPS-012`, `TST-001`, `TST-005`, `TST-009`
- **Scenario IDs:** `AC-OPS-002`, `AC-OPS-004`
- **Test class/method:** `src/test/js/delivery-contract.test.mjs`
- **Implementation commit:** `d13443e338770dec0ca9822600f9a9d8405dfdbb`

## Protected behavior

Gitea verification must reach Docker Desktop-published Testcontainers ports from
inside its job container. Container builds may start only after an equivalent
verification job succeeds, and the container workflow may run only by manual
dispatch or by a push to `main`. Every third-party workflow action is pinned to
the reviewed latest release commit rather than a moving tag.

## Test method

The dependency-free delivery contract reads both workflow files and checks the
Testcontainers host override, event filters, verify-to-build dependencies, and
the complete allowlist of immutable action SHAs. The remote failure log supplies
the production-shaped network reproduction because it ran inside the real Gitea
Docker runner.

## Hand-derived expected result

The runner already resolves `host.docker.internal` to its Docker host. Therefore
Testcontainers must use that host instead of the job-network gateway
`172.17.0.1`. Pull requests and non-main branch pushes must never schedule the
container workflow. Manual dispatches build but do not publish, while main pushes
publish only after verification succeeds.

## RED

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin \
  node --test src/test/js/delivery-contract.test.mjs

tea actions runs logs 174 --repo sechmachine/labtimesheet \
  --login sechmachine-git
```

**Observed result**

```text
Delivery contract: 4 tests, 1 passed, 3 failed. The workflows lacked the
Testcontainers host override, container event/dependency gates, and current
action pins.

Gitea run 174 found Docker at unix:///var/run/docker.sock but selected host
172.17.0.1. Ryuk started, then repeated connections to 172.17.0.1:57499 were
refused. Maven ended with 217 tests, 64 errors.
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin \
  node --test src/test/js/delivery-contract.test.mjs
```

**Observed result**

```text
Delivery contract: 4 tests, 4 passed.
```

## Affected suite

**Command and result**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin npm run test:ui
Result: 5 tests passed.

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
  PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/bin:/bin \
  DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock \
  ./mvnw -B test
Result: 205 tests passed across 44 suites; 0 failures, errors, or skips.

npm ci && npm run build
Result: Tailwind and Lucide assets built successfully; tracked assets remained unchanged.

./mvnw -B -DskipTests -Ddoclint=all javadoc:javadoc
Result: BUILD SUCCESS with 83 existing missing-comment warnings and no production Java change.

Ruby YAML parsing and git diff --check
Result: both workflow files parsed and the diff check passed.

Gitea Actions run 177 on `work/fix/platform/ci-testcontainers-actions`
Result: Verify completed successfully in 8 minutes on the real Docker-mode runner.
The non-main branch push scheduled `verify.yml` only; `container.yml` did not run.
```

## External-test boundaries

The local contract cannot prove action-runner compatibility, registry credentials,
or availability of the optional ARM runner. Those are checked by the actual Gitea
branch verification and main container runs. Release freshness was checked against
the official upstream release APIs on 2026-08-15; the immutable pins remain stable,
but a later release requires an intentional reviewed update.
