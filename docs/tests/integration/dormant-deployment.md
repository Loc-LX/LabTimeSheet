# Test Evidence: dormant immutable-SHA deployment template

- **Test type:** Integration
- **Requirement IDs:** `OPS-014`, `OPS-015`, `OPS-016`
- **Scenario IDs:** `AC-OPS-005`
- **Test class/method:** `src/test/js/delivery-contract.test.mjs` deployment template contract
- **Implementation commit:** `1f2590f9654fc670e0cf93d0c4960777db2cb5f7`
- **Documentation review-fix commit:** `1cd45526cfaadba46c632dd7ea5f77eb26e97b94`

## Protected behavior

The SSH deployment job is dormant unless the workflow is on `main`, `vars.DEPLOY_ENABLED` is exactly `true`, and all
four required deployment secrets (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PRIVATE_KEY`, `DEPLOY_KNOWN_HOSTS`) are
present. When explicitly enabled, it selects the full commit SHA's immutable AMD64 image tag, verifies the supplied
known-host file, pulls the image, updates the remote Compose environment, waits for the app healthcheck, and retains
the prior immutable image under the remote deployment state directory for rollback.

## Test method

The Node contract reads the workflow as an untrusted static artifact and asserts the exact main/variable/secret gate,
strict known-host SSH options, full-SHA validation, Docker pull/Compose rollout, health polling, previous-image state,
and absence of a `jobs.<job_id>.environment` approval boundary. No deployment secret is available to the test process,
and no SSH connection is attempted while the job is dormant.

`DEPLOYMENT.md` documents the same variable/secret gate, isolated runner files, remote
`/etc/labtimesheet/compose.env` and `deploy-state/previous-image` assumptions, immutable image selection, health
polling, and rollback behavior.

## Hand-derived expected result

Work-branch and pull-request executions must not schedule the deploy job. A manually enabled `main` execution may
reach it only after verification and the AMD64 image build; the remote script must fail closed for a non-full SHA or a
non-immutable current image and must attempt the retained previous SHA when the selected image fails rollout health.

## RED

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs
```

**Observed result**

```text
2026-08-22T14:29:21+07:00 — 5 tests, 4 passed, 1 failed. The new deployment-template contract failed at the first
missing `deploy` job gate on the base workflow; this was the expected missing-template RED.
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs
```

**Observed result**

```text
2026-08-22T14:29:36+07:00 — 5 tests, 5 passed, 0 failed, 0 skipped.
```

## Affected suite

**Commands and result**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin npm run test:ui
ruby -e 'require "yaml"; YAML.load_file(".gitea/workflows/container.yml"); puts "container workflow YAML: PASS"'
docker compose --env-file .env.compose.example config
docker compose --env-file .env.compose.example --profile bundled-db config
git diff --check
```

```text
Node UI/operational contracts: 8/8 passed. YAML parser: PASS. External and bundled Compose configs: PASS.
Whitespace check: PASS. No SSH or registry deployment was attempted; the job remains disabled by default.
```

## External-test boundaries

This evidence does not connect to a host, consume real secrets, pull from the private registry, or prove a production
rollback against live infrastructure. The job's enabled path is intentionally a future operator-controlled template;
the static contract proves its safety gates and command ordering only.
