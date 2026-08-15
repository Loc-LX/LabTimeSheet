# Test Evidence: Authorized Project pages

- **Test type:** Web
- **Requirement IDs:** `AUTH-001`, `AUTH-002`, `AUTH-006`, `PRJ-001`, `PRJ-004`–`PRJ-006`, `PRJ-012`, `SEC-001`, `ERR-001`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-002`, `AC-AUTH-007`, `AC-PRJ-006`, `I1-PRJ-04`, `I1-PRJ-05`
- **Test class/method:** `com.lab.labtimesheet.feature.project.controller.ProjectControllerTest`
- **Implementation commits:** `25a855e`, `a9ee99a`, `2f25731`, `dbf1202`, `af0eb3c`

## Protected behavior

Authenticated users receive only authorized Project routes; guessed IDs return the shared non-disclosing error contract; valid Mentor create requests use the authenticated identity; binding and domain validation re-render safe forms with retained input and no mutation. Completed owner/Admin/former-member views render without a current Leader or mutation forms. The planned-Project activation action is shown only to the owning Mentor; state changes require CSRF.

## Test method

MockMvc exercises the real controller, binding, Bean Validation, exception mapping, view selection, redirect, Spring Security authentication, and CSRF filter. Only application/query services are mocked.

## Hand-derived expected result

An authorized list request renders `projects/list`. Unauthorized and missing direct IDs produce the same `error/generic` view with `errorStatus`, `errorTitle`, and `errorMessage`; no exception detail is rendered. Member and leadership routes authorize through actor plus Project ID. A valid create redirects to the created detail ID; blank/date-invalid input and ineligible Leader/member selections retain safe input and render field errors without a successful mutation. An activation guard failure returns to detail with its safe rule message. Completed Project pages show no current Leader and no forms for owner, Admin, or former member. POST without CSRF returns 403.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectControllerTest test
```

**Observed result**

```text
[ERROR] cannot find symbol: class ProjectController
[ERROR] cannot find symbol: class ProjectPageService
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectControllerTest test
```

**Observed result**

```text
[INFO] Running com.lab.labtimesheet.feature.project.controller.ProjectControllerTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='Project*Test' test

[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Mentor-only control regression

**RED:** the focused MockMvc run reported two expected failures: `GET /projects/new` returned `200` for an Intern instead of non-disclosing `404`, and the member page rendered the `Add member` form for a non-owner.

**GREEN:** rerunning `./mvnw -Dtest=ProjectControllerTest test` after the controller/DTO/template correction passed 7 tests with zero failures, errors, or skips.

## Role-aware Project-list action regression

**RED:** the focused MockMvc run reported two expected failures after adding the list-action regression: the controller still resolved only a user ID, so the Mentor fixture was queried as user `0`, and an Intern-facing Project list rendered the `Create Project` link.

**GREEN:** rerunning `./mvnw -Dtest=ProjectControllerTest test` after resolving the public actor view and conditionally rendering the link passed 8 tests with zero failures, errors, or skips.

## Activation-route regression

**RED:** the focused MockMvc run reported two expected failures: `POST /projects/30/activate` returned `404`, and the owning Mentor's planned-Project detail did not render the `Activate` action.

**GREEN:** after adding the CSRF-protected POST route and owner/status-conditional Thymeleaf form, the two focused tests passed; the full `ProjectControllerTest` class passed 10 tests with zero failures, errors, or skips.

## Review round 1 safe-validation, completed-page, and error-contract regression

**RED command:**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=ProjectControllerTest test
```

**Observed RED:** `Tests run: 14, Failures: 5, Errors: 0`. Domain validation and activation returned blank 409 responses instead of their safe originating views; authorization/conflict responses had no `ModelAndView`; and completed detail rendered an empty Leader value instead of an explicit no-current-Leader state.

**Observed GREEN:** the same command passed the expanded owner/Admin/former-member matrix with `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`. The Project test resource supplies only a contract fixture for `error/generic`; Reporting/UI owns the production shared template.

## External-test boundaries

This slice does not prove PostgreSQL query correctness, a real login flow, shared-shell navigation, or live-browser accessibility. Reporting/UI owns the final production `error/generic` template and will consume the documented three-key model contract after merging this pin; Project deliberately does not edit that shared asset. Iteration 2 invitation/exit/completion pages remain out of scope. Server-side activation authorization and Task-assignee atomicity are covered by Project domain and PostgreSQL integration tests.
