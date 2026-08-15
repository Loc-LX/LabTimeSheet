# Test Evidence: Gitea container registry authentication

- **Test type:** Integration
- **Requirement IDs:** `OPS-012`, `OPS-016`, `OPS-017`
- **Scenario IDs:** `AC-OPS-004`
- **Test class/method:** `src/test/js/delivery-contract.test.mjs` — `container workflow runs only manually or on main and verifies before either image build`
- **Implementation commit:** `4dd9f96a231316ce2c14755157a380a2123c2f0b`

## Protected behavior

A push to `main` publishes `git.sechmachine.io.vn/sechmachine/labtimesheet` by authenticating the triggering Gitea account with the repository `REGISTRY_TOKEN`. Publication does not depend on separately configured image-name or username settings.

## Test method

The dependency-free Node contract reads the committed workflow and checks its fixed registry/image coordinates, actor-based username, token secret, and absence of the obsolete `CONTAINER_IMAGE` and `REGISTRY_USERNAME` settings. Ruby's YAML parser separately checks workflow syntax.

## Hand-derived expected result

The repository and package location are stable project facts. Therefore the workflow needs one credential only: a token belonging to the triggering actor with package read/write permission. Manual dispatch still builds without publishing; only a `main` push logs in and publishes.

## RED

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs
```

**Observed result**

```text
4 tests ran: 3 passed, 1 failed. The container contract could not find the fixed registry/image or actor-based login. Real Gitea Container run 179 independently failed before registry login with "Repository variable CONTAINER_IMAGE is required", so REGISTRY_TOKEN was never used.
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin node --test src/test/js/delivery-contract.test.mjs
ruby -e 'require "yaml"; YAML.safe_load(File.read(".gitea/workflows/container.yml"), aliases: true); puts "container workflow YAML: OK"'
```

**Observed result**

```text
Delivery contract: 4 tests, 4 passed. Container workflow YAML: OK.
```

## Affected suite

**Command and result**

```text
git diff --check
! rg -n 'CONTAINER_IMAGE|REGISTRY_USERNAME' .gitea/workflows/container.yml DEPLOYMENT.md

Both checks passed. Application tests were deliberately not repeated because the change is limited to workflow metadata, its contract test, and deployment guidance; the container workflow retains its mandatory verify job before building.
```

## External-test boundaries

Local checks do not authenticate to the private registry. The first `main` push containing this change is the production-shaped check of `REGISTRY_TOKEN`, package permissions, and registry publication.
