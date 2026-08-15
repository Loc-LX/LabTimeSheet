# Test Evidence: persistent SMTP warning and accessible onboarding shell

- **Test type:** Web
- **Requirement IDs:** `ACC-005`, `ACC-006`, `ACC-007`, `INT-007`, `UI-004`, `UI-007`, `UI-010`, `I1-UI-01`, `I1-UI-02`, `I1-UI-04`
- **Scenario IDs:** `AC-ACC-003`, `AC-UI-001`, `AC-UI-002`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.DashboardControllerWebTest`, `com.lab.labtimesheet.feature.integration.controller.SmtpOnboardingWebIntegrationTest`, `com.lab.labtimesheet.feature.account.controller.BootstrapOnboardingWebIntegrationTest#fiveDistinctDeferralConfirmationsAreSequentialAndOnlyTheLastCanFinish`
- **Implementation commit:** `ddf688a`

## Protected behavior

An Admin without active SMTP sees a persistent, actionable restricted-installation warning on every shared-shell page, including the dashboard reached after the fifth deferral confirmation. The warning is absent for non-Admins and after SMTP activation. SMTP configuration and deferral reuse the authenticated desktop shell and its pre-paint theme, focus, local assets, navigation, and logout behavior. Invalid SMTP fields expose a single accessible error summary plus stable field-error IDs referenced by the corresponding controls, while safe fields are retained and the submitted password is never rendered.

## Test method

The reporting MVC slice renders the production Admin and Mentor dashboard templates with real Spring Security principals and only the dashboard and SMTP services mocked at their public boundaries. PostgreSQL 18.4 integration tests bootstrap a real Admin, traverse all five server-owned deferral steps, finish onto the real dashboard, and render a representative account page. The SMTP integration test submits every supported invalid field combination through the real controller, Jakarta Validation, Thymeleaf binding, and production template. A separate invalid request proves safe-value retention and request-local password clearing.

## Hand-derived expected result

With no active SMTP, an Admin dashboard and account page contain the exact warning and an `/admin/smtp` action. A Mentor dashboard never contains that warning, and an Admin page after activation does not contain it. The first through fourth deferral steps do not expose Finish; the fifth does; Finish redirects to `/dashboard`, where the warning persists. Host, port, security mode, authentication completeness, From address, and From name each render a unique error ID and the associated invalid control references that ID through `aria-describedby`. The global summary is labeled, safe username/sender values remain, and the submitted password is absent.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=DashboardControllerWebTest,SmtpOnboardingWebIntegrationTest,BootstrapOnboardingWebIntegrationTest test
```

**Observed result**

```text
Tests run: 13, Failures: 3, Errors: 0, Skipped: 0
DashboardControllerWebTest: Admin dashboard did not contain the persistent restricted-installation warning or SMTP action.
SmtpOnboardingWebIntegrationTest: SMTP form did not load /assets/theme.js because it was still standalone.
BootstrapOnboardingWebIntegrationTest: SMTP deferral did not load /assets/theme.js because it was still standalone.
BUILD FAILURE
```

The initial XPath assertion attempt was discarded before implementation because the HTML5 doctype is not XML-parseable by MockMvc's XML XPath matcher. The corrected string-based run above is the recorded behavior RED.

A follow-up focused RED for the authentication-pair error ran one PostgreSQL-backed method and failed 1/1 because the password referenced `smtp-authentication-error` but the paired username did not. Associating both controls made the identical command pass 1/1.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=DashboardControllerWebTest,SmtpOnboardingWebIntegrationTest,BootstrapOnboardingWebIntegrationTest test
```

**Observed result**

```text
PostgreSQL 18.4 via Testcontainers
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 30.179 s
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
npm run build
./mvnw -Dtest=SecurityResponseIntegrationTest,BootstrapOnboardingWebIntegrationTest,AccountWebIntegrationTest,SmtpOnboardingWebIntegrationTest,RoleDashboardWebIntegrationTest,UiContractWebTest,AccountTemplateIntegrationTest,AttendanceTemplateIntegrationTest,DashboardControllerWebTest,DashboardTemplateWebTest,ProjectTaskFormAccessibilityWebTest,SharedErrorTemplateWebTest,ProjectControllerTest,TaskControllerTest,AttendanceControllerTest test

Node v24.19.0; npm 11.17.0; Tailwind CSS v4.3.3
Two consecutive builds produced app.css SHA-256 f0a4abbffaf66581ee7e17952743e591b8957e0cbcd19099e234d13827700e4c and icons.svg SHA-256 001f72c93967f816fdd56f3f9b34cb5e5831b8b8c572d051669c6a3aae2c3cda.
PostgreSQL 18.4 via Testcontainers
Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 50.418 s
```

## Full verification

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

PostgreSQL 18.4 via Testcontainers
Tests run: 197, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 01:24 min

./mvnw -DskipTests compile
BUILD SUCCESS
Total time: 0.764 s

./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS
Total time: 0.924 s
```

## External-test boundaries

MockMvc verifies rendered security visibility, form binding, CSRF-generated forms, exact deferral ordering, safe retained values, and accessibility associations. It does not prove viewport overflow, keyboard focus rendering, collapse behavior, or visually observable theme flash; the separate real-browser evidence covers those boundaries. SMTP transport remains represented by the existing test probe and no real mail server is required.
