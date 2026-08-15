# Test Evidence: Development environment configuration

- **Test type:** Integration
- **Requirement IDs:** `OPS-001`, `OPS-004`, `SEC-013`
- **Scenario IDs:** `AC-OPS-001`, `AC-SEC-005`
- **Test class/method:** Shell configuration contract plus the full Spring Boot Maven suite
- **Implementation commit:** `4212e9cbc2791e0c733929af62503df26097431e`

## Protected behavior

Development starts from environment-backed datasource, encryption, public-origin, server, proxy, and local Mailpit settings without committing a real `.env` or weakening application security controls.

## Test method

A shell contract verifies that the committed placeholder and development properties exist, the superseded YAML is absent, the real `.env` is ignored, every required environment key is represented, and every application placeholder resolves after loading the local file. The full Maven suite then exercises Spring configuration binding, Flyway, JPA validation, security, and PostgreSQL behavior.

## Hand-derived expected result

The committed tree contains `.env.example` and `application-dev.properties`, never tracks `.env`, and exposes exactly the environment inputs needed by the current application. Loading the local file gives Spring a `dev` profile, PostgreSQL connection, 32-byte Base64 encryption key, public origin, local Mailpit endpoint, server port, and explicit no-forwarded-header policy.

## RED

**Command**

```text
required_files=(.env.example src/main/resources/application-dev.properties); failed=0; for file in $required_files; do if [ ! -f "$file" ]; then echo "MISSING $file"; failed=1; fi; done; if [ -f src/main/resources/application-dev.yaml ]; then echo 'STALE src/main/resources/application-dev.yaml'; failed=1; fi; if ! grep -qx '/.env' .gitignore; then echo 'MISSING /.env ignore rule'; failed=1; fi; exit "$failed"
```

**Observed result**

```text
MISSING .env.example
MISSING src/main/resources/application-dev.properties
STALE src/main/resources/application-dev.yaml
MISSING /.env ignore rule
exit 1
```

## GREEN

**Command**

```text
required_files=(.env.example src/main/resources/application-dev.properties); required_env=(SPRING_PROFILES_ACTIVE LAB_SERVER_PORT LAB_FORWARD_HEADERS_STRATEGY LAB_DB_URL LAB_DB_USERNAME LAB_DB_PASSWORD LAB_SMTP_HOST LAB_SMTP_PORT LAB_PUBLIC_ORIGIN LAB_SECURITY_MASTER_KEY); required_props=(server.port server.forward-headers-strategy spring.datasource.url spring.datasource.username spring.datasource.password spring.jpa.hibernate.ddl-auto spring.jpa.open-in-view spring.flyway.enabled spring.mail.host spring.mail.port lab.public-origin lab.security.master-key); failed=0; for file in $required_files; do if [ ! -f "$file" ]; then echo "MISSING $file"; failed=1; fi; done; if [ -f src/main/resources/application-dev.yaml ]; then echo 'STALE src/main/resources/application-dev.yaml'; failed=1; fi; if ! grep -qx '/.env' .gitignore; then echo 'MISSING /.env ignore rule'; failed=1; fi; for key in $required_env; do if ! grep -q "^${key}=" .env.example; then echo "MISSING example $key"; failed=1; fi; if ! grep -q "^${key}=" .env; then echo "MISSING local $key"; failed=1; fi; done; for property in $required_props; do if ! grep -q "^${property}=" src/main/resources/application-dev.properties; then echo "MISSING property $property"; failed=1; fi; done; set -a; source .env; set +a; decoded_bytes=$(printf '%s' "$LAB_SECURITY_MASTER_KEY" | base64 -d | wc -c | tr -d ' '); if [ "$decoded_bytes" != 32 ]; then echo "INVALID master key bytes=$decoded_bytes"; failed=1; fi; if ! git check-ignore -q .env; then echo 'LOCAL .env is not ignored'; failed=1; fi; if git ls-files --error-unmatch .env >/dev/null 2>&1; then echo 'LOCAL .env is tracked'; failed=1; fi; if [ "$failed" -eq 0 ]; then echo 'development configuration contract: PASS'; fi; exit "$failed"
```

**Observed result**

```text
development configuration contract: PASS

A real Java 25 process loaded `.env` and `application-dev.properties`, connected to PostgreSQL 18.4, validated Flyway/JPA, and started on the environment-overridden port 18081. With temporary Mailpit on the configured SMTP port, `/actuator/health` returned HTTP 200 with `UP`, and `/login` returned HTTP 200. The process shut down and the temporary Mailpit container was removed.
```

## Affected suite

**Command and result**

```text
env -u SPRING_PROFILES_ACTIVE -u LAB_SERVER_PORT -u LAB_FORWARD_HEADERS_STRATEGY -u LAB_DB_URL -u LAB_DB_USERNAME -u LAB_DB_PASSWORD -u LAB_SMTP_HOST -u LAB_SMTP_PORT -u LAB_PUBLIC_ORIGIN -u LAB_SECURITY_MASTER_KEY /bin/zsh -lc 'export JAVA_HOME=/opt/homebrew/opt/openjdk@25; export PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH; export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock; ./mvnw test'

Tests run: 197, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS in 01:33 using PostgreSQL 18.4 Testcontainers. No development environment value was present.
```

## External-test boundaries

The committed example cannot prove another developer's local credentials. Product SMTP and HolidayAPI revisions remain Admin-console configuration and are intentionally absent from `.env`.
