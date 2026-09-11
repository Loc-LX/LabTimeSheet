# Lab Timesheet Technology Stack

This document is the team reference for the technologies and development tools
used by Lab Timesheet. The five-person development team uses Windows 11 and
IntelliJ IDEA. Versions controlled by the repository must not be changed in one
developer's environment without a reviewed project-wide update.

## 1. Architecture

| Choice | Use | Rationale |
|---|---|---|
| Server-rendered modular monolith | One Spring Boot application organized by account, integration, Project, Task, attendance, notification, and reporting features | A single deployable application keeps transactions, authorization, testing, and deployment manageable for a small team while feature packages preserve clear ownership. |
| Spring MVC with Thymeleaf | Controllers return HTML pages rendered on the server | The product is a form- and workflow-heavy desktop web application. Server rendering avoids the extra API, SPA state, and authentication complexity of a separate frontend application. |
| Feature-first Java packages | Each feature owns its controllers, DTOs, entities, repositories, services, and exceptions | Related code stays together, while cross-feature access is limited to public services and DTOs. This supports the five-branch team workflow without duplicating database models. |
| Executable WAR | Maven packages the application as a WAR that can still run with `java -jar` | It works with the current Spring Boot deployment while keeping conventional servlet-container compatibility. |
| HTML sessions and CSRF protection | Spring Security manages authenticated browser sessions | The application is server-rendered. Session cookies and CSRF protection are simpler and safer here than introducing JWTs. |

The project deliberately does not use a SPA framework, JWT authentication,
microservices, Redis, Kafka, or a generic workflow engine. Those technologies
would add operational and development cost without solving a current need.

## 2. Team workstations

| Tool | Team baseline | Use | Rationale |
|---|---|---|---|
| Windows | Windows 11 | Team development operating system | One shared OS baseline makes IDE, Docker, path, and command guidance reproducible for the student team. |
| IntelliJ IDEA | Current supported release | Main IDE for Java, Maven, Spring Boot, Thymeleaf, debugging, and test execution | IntelliJ has strong Spring and Java support and provides one consistent run/debug workflow for the team. |
| Eclipse Temurin JDK | Java 25 | Compile, test, and run the application | Java 25 is the project language baseline, and Temurin matches the JDK distribution used by CI and container builds. |
| IntelliJ annotation processing | Enabled for the project | Makes Lombok-generated constructors and accessors visible to the IDE | Maven already runs Lombok as an annotation processor. Enabling the same behavior in IntelliJ prevents false editor errors. |
| Git | Current supported release | Version control and the branch/worktree workflow | Git supports the five persistent feature branches, isolated fix branches, review, and traceable milestone commits. |
| PowerShell | Included with Windows 11 | Run Windows commands and `mvnw.cmd` | It is available on every team workstation and avoids requiring a separate shell for normal development. |
| OpenSSL | Current supported release | Generates the Base64 256-bit application master key | A standard cryptographic tool avoids inventing or manually typing security keys. Git for Windows or another trusted Windows package may provide it. |
| Microsoft Edge or Google Chrome | Current stable release | Manual desktop-browser checks and debugging | The product targets desktop browsers, and both provide standards-based developer tools for HTML, CSS, accessibility, storage, and network inspection. |

## 3. Java and build platform

| Technology | Version | Use | Rationale |
|---|---:|---|---|
| Java | 25 | Application language and runtime | Provides a modern supported Java baseline while keeping one version across local development, CI, and production. |
| Spring Boot | 4.1.0 | Application framework and dependency management | Supplies compatible Spring modules, production conventions, testing support, and a managed dependency set. |
| Maven Wrapper | Wrapper 3.3.4; Maven 3.9.16 | Java dependency resolution, compilation, tests, Javadoc, and packaging | The checked-in wrapper gives every Windows workstation and CI runner the same Maven version without a separate Maven installation. Use `mvnw.cmd` on Windows. |
| Javadoc with doclint | Java 25 toolchain | Validates generated API documentation | Documentation errors are caught during development and CI with the same JDK that compiles the application. |
| Lombok | Spring Boot-managed version | Removes mechanical constructors and accessors | Targeted Lombok annotations reduce boilerplate while explicit domain constructors, validation, state changes, and entity identity methods remain visible. |
| Spring Boot configuration processor | Spring Boot-managed version | Generates typed Spring configuration metadata during compilation | It improves configuration accuracy and IntelliJ assistance without adding runtime code. |
| Embedded/provided Tomcat | Spring Boot-managed version | Servlet runtime for the executable WAR | It is the standard Spring MVC runtime and requires no separate application server for development or the production image. |

Unless a row gives an explicit version, Java dependency versions are managed by
the Spring Boot 4.1.0 dependency set. This prevents individual libraries from
being upgraded into incompatible combinations.

## 4. Spring and Java dependencies

| Dependency | Use | Rationale |
|---|---|---|
| Spring Web MVC | Controllers, request binding, validation errors, and server-rendered routes | It matches the Thymeleaf form workflow and keeps browser navigation on the server. |
| Spring Security | Login, password hashing, sessions, CSRF, role checks, and security headers | Security rules stay in the established Spring filter and authorization model rather than custom code. |
| Spring Data JPA and Hibernate | Entity mapping, repositories, transactions, optimistic locking, and pessimistic locks | JPA removes routine persistence code while PostgreSQL and Flyway remain the schema authority. |
| Spring Validation / Jakarta Validation | Request DTO and configuration validation | Validation annotations provide consistent trust-boundary checks and actionable form errors. |
| Thymeleaf | HTML page and email-template rendering | Templates integrate directly with Spring MVC and work without a client-side framework. |
| Thymeleaf Spring Security extras | Role- and authentication-aware template rendering | Navigation and controls can reflect server authorization without duplicating role parsing. |
| Spring Mail | SMTP connectivity and email delivery | Uses the standard Jakarta Mail integration while SMTP settings remain Admin-managed application data. |
| Spring Boot Actuator | Liveness, readiness, and application health | Standard health endpoints support Docker health checks and production operations. |
| Spring Boot Flyway integration | Runs reviewed database migrations at startup | Database changes are ordered, repeatable, and validated before Hibernate mappings are used. |
| Flyway PostgreSQL support | PostgreSQL-specific migration support | The schema uses PostgreSQL features such as `btree_gist`, checks, partial indexes, and exclusion constraints. |
| PostgreSQL JDBC driver | Runtime database connection | It is the official Java driver for the selected database. |
| Spring Boot DevTools | Development-only restart support | Shortens the local feedback loop without becoming a production dependency. |
| Spring Boot Docker Compose support | Optional runtime integration | It is available for Spring tooling, although the documented development loop currently starts PostgreSQL and Mailpit explicitly. |

## 5. Database and persistence

| Technology | Version | Use | Rationale |
|---|---:|---|---|
| PostgreSQL | 18.4 | Development, test, and production database | The domain requires reliable transactions, constraints, date/time types, partial indexes, exclusion constraints, and strong concurrency behavior. Using the same engine everywhere avoids H2-specific surprises. |
| Flyway | Spring Boot-managed version | Versioned schema migrations | Flyway makes the reviewed SQL schema reproducible on an empty database and safe to validate in CI. |
| Hibernate schema validation | `ddl-auto=validate` | Confirms entity mappings match the migrated schema | Hibernate must not silently create or alter production tables; Flyway remains authoritative. |
| PostgreSQL `btree_gist` | Database extension | Supports exclusion constraints such as overlapping leave prevention | The database can reject invalid concurrent data even when two application requests race. |

## 6. Frontend stack

| Technology | Version | Use | Rationale |
|---|---:|---|---|
| Thymeleaf | Spring Boot-managed version | Page layouts, fragments, forms, validation messages, and role-aware navigation | It keeps rendering and authorization close to the Spring MVC application. |
| HTML5 | Browser standard | Semantic forms, tables, native dialogs, and accessible page structure | Native elements reduce custom JavaScript and provide built-in keyboard and form behavior. |
| Tailwind CSS | 4.3.3 | Compiled design tokens and utility-based styling | It supports the shared light/dark desktop design without shipping a runtime CSS framework. |
| Tailwind CLI | 4.3.3 | Builds the committed production CSS asset | The small CLI is sufficient; no frontend bundler or SPA toolchain is needed. |
| Lucide Static | 1.27.0 | Local SVG icon sprite | Icons are available offline, inherit theme color, and do not require React or an icon CDN. |
| Native JavaScript modules | Browser standard | Small interactions such as theme selection, sidebar state, dialogs, and local search | The current interactions do not justify a client-side application framework. |
| Node.js | 24.x | Frontend build scripts and JavaScript tests | Node 24 is the pinned LTS toolchain used consistently by developers, CI, and the Docker build. |
| npm | 11.x | Reproducible frontend dependency installation | `npm ci` and the committed lockfile install exactly the reviewed dependency graph. |

## 7. External services and integrations

| Service or standard | Use | Rationale |
|---|---|---|
| SMTP | Activation, password recovery, and ordinary workflow email | SMTP is widely supported and allows the application to work with university or other approved mail providers. Configuration is tested and activated through the Admin console. |
| Mailpit | Development SMTP server and web inbox | Mailpit captures messages locally so developers never send test activation or recovery email to real users. The development image is pinned to `axllent/mailpit:v1.27.4`. |
| HolidayAPI | Optional Vietnam holiday preview/import | It reduces manual holiday entry while imported dates remain a preview and the Admin's local day-off decision remains authoritative. |
| AES-256-GCM from the JDK | Encrypts stored SMTP and HolidayAPI secrets | Authenticated encryption protects confidentiality and detects modification without adding another cryptography dependency. |
| HTTPS reverse proxy | Production TLS termination and forwarding | The application image stays focused on Java while an operator-managed proxy handles certificates and the public HTTPS endpoint. |

## 8. Testing tools

| Tool | Use | Rationale |
|---|---|---|
| JUnit Jupiter | Unit and integration test framework | It is the standard JUnit 5 programming model supplied by Spring Boot and works with Maven Surefire and IntelliJ. |
| Maven Surefire | Maven/Spring Boot-managed version | Discovers and runs the Java test suite | The same Maven command behaves consistently in IntelliJ terminals, PowerShell, and CI. |
| Spring Boot Test | Application-context and integration testing | It verifies real Spring configuration, dependency injection, transactions, and profile behavior. |
| Focused Spring Boot test starters | Data JPA, Flyway, Mail, Security, Thymeleaf, Validation, and Web MVC test support | Each test slice receives the framework support it actually exercises instead of one unrelated test environment. |
| Spring MVC Test / MockMvc | Controller, security, validation, and Thymeleaf route tests | HTTP behavior can be tested quickly without launching a separate browser process. |
| Spring Security Test | Authenticated role and CSRF test support | Tests can prove allowed and denied behavior using the same security filter chain. |
| Mockito | Test doubles for external or out-of-scope collaborators | It isolates a focused unit or MVC slice without replacing the database behavior being tested. |
| Testcontainers | Spring Boot-managed version | Starts disposable infrastructure for integration tests | Tests use real PostgreSQL 18.4 without depending on a developer's database or leaving shared state behind. |
| Testcontainers PostgreSQL | PostgreSQL 18.4 test container integration | It validates Flyway SQL, JPA mappings, constraints, locking, and concurrency against the production database engine. |
| Node built-in test runner | Frontend asset and workflow contract tests | The required JavaScript checks run without adding another test framework. |
| Playwright | Automated desktop-browser end-to-end journeys | Playwright provides repeatable Chromium-based tests for bootstrap, login, role navigation, Projects, Tasks, attendance, and accessibility-sensitive workflows required by the instructor. |
| Manual Edge/Chrome journeys | Exploratory and final visual checks | Manual checks still catch layout, focus, contrast, and real-browser integration issues that focused automated tests may not explain clearly. |
| Javadoc rule traces | The numbered requirements a test protects, named in the test source | The trace survives a rename and is read back mechanically, so the report of untested rules is generated rather than maintained. See `ADR-004`. |

## 9. Reporting technologies

These tools are approved for the reporting iteration. They must be added with
reviewed, pinned versions when their corresponding feature is implemented.

| Technology | Approved baseline | Use | Rationale |
|---|---:|---|---|
| Chart.js | 4.5.1 | Meaningful attendance and Project trend charts | It provides accessible, lightweight charts without changing the server-rendered architecture; every chart also requires a text or table alternative. |
| Apache POI XSSF | Compatible 5.5.x | Excel `.xlsx` exports | POI is the established Java library for native Excel workbooks and supports typed cells and formatting. |
| OpenPDF `openpdf-html` | Compatible 3.0.x | PDF generation from a dedicated print-safe template | It keeps PDF generation inside Java and supports an embedded Unicode font for Vietnamese content. |

## 10. Containers and production delivery

| Technology | Version or baseline | Use | Rationale |
|---|---:|---|---|
| Docker Desktop | Current supported Windows release using Linux containers | Development infrastructure, Testcontainers, and local production-image checks | It provides the Docker Engine expected by PostgreSQL, Mailpit, Testcontainers, and multi-stage builds on Windows 11. |
| Docker Compose | v2.20 or newer | Production example with bundled or external PostgreSQL | One documented file supports both deployment topologies while retaining persistent database storage. |
| Docker BuildKit / Buildx | Current workflow-pinned release | Multi-stage and multi-architecture image builds | Buildx produces native Linux AMD64 and optional ARM64 images with reproducible build stages. |
| Node Alpine image | Node 24, digest-pinned | Builds Tailwind and Lucide assets | Frontend tools do not remain in the final Java runtime image. |
| Eclipse Temurin images | Java 25 JDK and JRE, digest-pinned | Builds the WAR and runs the production application | Separate build and runtime images reduce the final image size and match the Java baseline. |
| PostgreSQL image | 18.4, digest-pinned | Optional bundled production database | Digest pinning prevents an image tag from silently changing during deployment. |
| OCI image registry | Gitea package registry | Stores immutable application images | Commit-SHA tags make a deployed version identifiable and allow a controlled rollback. |

The final application container runs as non-root UID/GID `10001`, uses a
read-only root filesystem in Compose, drops Linux capabilities, and exposes
Actuator readiness and liveness checks.

## 11. Source control and CI/CD

| Tool | Use | Rationale |
|---|---|---|
| Gitea | Git hosting, review, Actions, and OCI package registry | One project-owned platform stores source, reviews changes, runs checks, and publishes production images. |
| Gitea Actions | Verification on every pull request and push | CI repeats frontend, Java, PostgreSQL, Javadoc, generated-asset, and whitespace checks outside a developer workstation. |
| Container workflow | Manual dispatch or `main` push only | Image builds are expensive and potentially publish artifacts, so they run only after an internal verification job and never for ordinary feature branches or pull requests. |
| `actions/checkout` | 7.0.1, immutable SHA pin | Checks out source without retaining push credentials | An immutable pin prevents a moving action tag from changing CI behavior unexpectedly. |
| `actions/setup-java` | 5.7.0, immutable SHA pin | Installs Temurin Java 25 and manages the Maven cache | CI uses the same Java baseline as the team and production build. |
| `actions/setup-node` | 7.0.0, immutable SHA pin | Installs Node 24 and manages the npm cache | CI uses the same frontend toolchain as the lockfile and Docker build. |
| Docker Buildx action | 4.2.0, immutable SHA pin | Prepares multi-architecture image building | It supports native AMD64 and optional native ARM64 production builds. |
| Docker Login action | 4.6.0, immutable SHA pin | Authenticates only publication jobs to the registry | Registry credentials stay out of scripts and are used only when publishing is authorized. |
| Docker Build Push action | 7.3.0, immutable SHA pin | Builds and publishes OCI images | It provides one reviewed image-build path for both supported Linux architectures. |

## 12. Configuration and source-of-truth files

| File | Controls |
|---|---|
| `pom.xml` | Java version, Spring Boot version, Java dependencies, packaging, and annotation processors |
| `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper and Maven distribution |
| `package.json` and `package-lock.json` | Node/npm baseline and exact frontend dependencies |
| `src/main/resources/application*.yaml` | Shared, development, and production Spring configuration |
| `src/main/resources/db/migration/` | Flyway database schema history |
| `Dockerfile` | Production multi-stage application image |
| `compose.yaml` | Production application and optional PostgreSQL deployment example |
| `.gitea/workflows/` | Verification and container publication workflows |
| `DEVELOPMENT.md` | Windows/IDE-oriented local setup and run instructions |
| `TESTING.md` | Test commands, TDD rules, and evidence format |
| `DEPLOYMENT.md` | Production container configuration and operation |

When documentation and a build file disagree about an installed version, the
build file and lockfile are authoritative. Update this document in the same
reviewed change whenever the selected stack changes.
