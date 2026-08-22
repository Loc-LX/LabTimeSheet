# Test Evidence: dormant immutable-SHA deployment template

- **Test type:** Integration
- **Requirement IDs:** `OPS-014`, `OPS-015`, `OPS-016`
- **Scenario IDs:** `AC-OPS-005`
- **Test class/method:** `src/test/js/delivery-contract.test.mjs` deployment template contract
- **Implementation commit:** pending
- **Documentation review-fix commit:** `1cd45526cfaadba46c632dd7ea5f77eb26e97b94`

## Protected behavior

The SSH deployment job is dormant unless the workflow is on `main`, `vars.DEPLOY_ENABLED` is exactly `true`, and all
four required deployment secrets (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PRIVATE_KEY`, `DEPLOY_KNOWN_HOSTS`) are
present. When explicitly enabled, it selects the full commit SHA's immutable AMD64 image tag, verifies the supplied
known-host file, passes the absolute `/etc/labtimesheet/compose.yaml` path alongside `/etc/labtimesheet/compose.env`,
and uses that file for Compose rollout, health lookup, and rollback while retaining the prior immutable image under
the remote deployment state directory.

## Test method

The Node contract reads the workflow as an untrusted static artifact and asserts the exact main/variable/secret gate,
strict known-host SSH options, full-SHA validation, Docker pull/Compose rollout, health polling, previous-image state,
and absence of a `jobs.<job_id>.environment` approval boundary. No deployment secret is available to the test process,
and no SSH connection is attempted while the job is dormant.

`DEPLOYMENT.md` documents the same variable/secret gate, isolated runner files, remote
`/etc/labtimesheet/compose.env`, `/etc/labtimesheet/compose.yaml`, and `deploy-state/previous-image` assumptions,
immutable image selection, health polling, and rollback behavior.

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
2026-08-22T16:31:45+07:00 — 6 tests, 5 passed, 1 failed. The new absolute remote Compose-file contract failed because
the enabled workflow passed only the env-file path and invoked Compose without `-f`; this was the expected missing
behavior RED.
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs
```

**Observed result**

```text
2026-08-22T16:32:05+07:00 — 6 tests, 6 passed, 0 failed, 0 skipped. The enabled deployment now passes and validates
`/etc/labtimesheet/compose.yaml`; rollout, health lookup, and rollback all call the same absolute file.
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
Node UI/operational contracts: 9/9 passed. YAML parser: PASS. External and bundled Compose configs: PASS.
Whitespace check: PASS. No SSH or registry deployment was attempted; the job remains disabled by default.
```

## External-test boundaries

This evidence does not connect to a host, consume real secrets, pull from the private registry, or prove a production
rollback against live infrastructure. The job's enabled path is intentionally a future operator-controlled template;
the static contract proves its safety gates and command ordering only.
