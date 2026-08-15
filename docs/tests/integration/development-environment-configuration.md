# Test Evidence: Development environment configuration

- **Test type:** Integration
- **Requirement IDs:** `OPS-001`, `OPS-004`, `SEC-013`
- **Scenario IDs:** `AC-OPS-001`, `AC-SEC-005`
- **Test class/method:** Shell configuration contract, an unsourced dev-profile startup probe, and the full Spring Boot Maven suite
- **Implementation commit:** `531c6078521341b156d69cb1013e54f65c092311`

## Protected behavior

Development starts from datasource, encryption, public-origin, server, proxy,
and local Mailpit values in the ignored root `.env` file without requiring an
IDE-specific environment-variable copy, committing secrets, or weakening
application security controls.

## Test method

A shell contract verifies that the committed placeholder and development YAML
exist, the superseded properties file is absent, the YAML explicitly imports
the ignored root `.env`, and every required placeholder remains represented. A
real dev-profile process is launched without sourcing `.env` to prove Spring
loads it. The full Maven suite then exercises Spring configuration binding,
Flyway, JPA validation, security, and PostgreSQL behavior.

## Hand-derived expected result

The committed tree contains `.env.example` and `application-dev.yaml`, never
tracks `.env`, and exposes exactly the inputs needed by the current
application. Starting the `dev` profile from the repository root, without
exporting the file, gives Spring the PostgreSQL connection, 32-byte Base64
encryption key, public origin, local Mailpit endpoint, server port, and explicit
forwarded-header policy.

## RED

**Command**

```text
/bin/zsh -lc 'config_check_failed=0; if test -e src/main/resources/application-dev.properties; then echo "STALE application-dev.properties"; config_check_failed=1; fi; if ! test -f src/main/resources/application-dev.yaml; then echo "MISSING application-dev.yaml"; config_check_failed=1; fi; if test -f src/main/resources/application-dev.yaml && ! grep -Fq "optional:file:\${LAB_DEV_ENV_FILE:.env}[.properties]" src/main/resources/application-dev.yaml; then echo "MISSING .env import"; config_check_failed=1; fi; exit "$config_check_failed"'
```

**Observed result**

```text
STALE application-dev.properties
MISSING application-dev.yaml
exit 1
```

## GREEN

**Command**

```text
/bin/zsh -lc 'dev_contract_failed=0; dev_required_files=(.env.example src/main/resources/application-dev.yaml); dev_required_env=(SPRING_PROFILES_ACTIVE LAB_SERVER_PORT LAB_FORWARD_HEADERS_STRATEGY LAB_DB_URL LAB_DB_USERNAME LAB_DB_PASSWORD LAB_SMTP_HOST LAB_SMTP_PORT LAB_PUBLIC_ORIGIN LAB_SECURITY_MASTER_KEY); dev_required_placeholders=(LAB_SERVER_PORT LAB_FORWARD_HEADERS_STRATEGY LAB_DB_URL LAB_DB_USERNAME LAB_DB_PASSWORD LAB_SMTP_HOST LAB_SMTP_PORT LAB_PUBLIC_ORIGIN LAB_SECURITY_MASTER_KEY); for dev_file in $dev_required_files; do if ! test -f "$dev_file"; then echo "MISSING $dev_file"; dev_contract_failed=1; fi; done; if test -e src/main/resources/application-dev.properties; then echo "STALE application-dev.properties"; dev_contract_failed=1; fi; if ! grep -Fq "optional:file:\${LAB_DEV_ENV_FILE:.env}[.properties]" src/main/resources/application-dev.yaml; then echo "MISSING .env import"; dev_contract_failed=1; fi; if ! grep -qx "/.env" .gitignore; then echo "MISSING /.env ignore rule"; dev_contract_failed=1; fi; for dev_key in $dev_required_env; do if ! grep -q "^${dev_key}=" .env.example; then echo "MISSING example $dev_key"; dev_contract_failed=1; fi; if ! grep -q "^${dev_key}=" .env; then echo "MISSING local $dev_key"; dev_contract_failed=1; fi; done; for dev_key in $dev_required_placeholders; do dev_placeholder="\${${dev_key}}"; if ! grep -Fq "$dev_placeholder" src/main/resources/application-dev.yaml; then echo "MISSING YAML placeholder $dev_key"; dev_contract_failed=1; fi; done; set -a; source .env; set +a; dev_decoded_key_bytes=$(printf "%s" "$LAB_SECURITY_MASTER_KEY" | base64 -d | wc -c | tr -d " "); if test "$dev_decoded_key_bytes" != 32; then echo "INVALID master key bytes=$dev_decoded_key_bytes"; dev_contract_failed=1; fi; if ! git check-ignore -q .env; then echo "LOCAL .env is not ignored"; dev_contract_failed=1; fi; if git ls-files --error-unmatch .env >/dev/null 2>&1; then echo "LOCAL .env is tracked"; dev_contract_failed=1; fi; if test "$dev_contract_failed" -eq 0; then echo "development configuration contract: PASS"; fi; exit "$dev_contract_failed"'
```

**Observed result**

```text
development configuration contract: PASS
```

An additional Java 25 process was started with all `LAB_*` and
`SPRING_PROFILES_ACTIVE` environment variables removed. It loaded the root
`.env` through `application-dev.yaml`, connected to PostgreSQL 18.4, validated
Flyway/JPA, and started successfully. The process was then stopped cleanly.

```text
env -u SPRING_PROFILES_ACTIVE -u LAB_SERVER_PORT -u LAB_FORWARD_HEADERS_STRATEGY -u LAB_DB_URL -u LAB_DB_USERNAME -u LAB_DB_PASSWORD -u LAB_SMTP_HOST -u LAB_SMTP_PORT -u LAB_PUBLIC_ORIGIN -u LAB_SECURITY_MASTER_KEY -u LAB_DEV_ENV_FILE /bin/zsh -lc 'export JAVA_HOME=/opt/homebrew/opt/openjdk@25; export PATH="$JAVA_HOME/bin:$PATH"; ./mvnw -DskipTests spring-boot:run'

No active profile set, falling back to 1 default profile: "dev"
Database: jdbc:postgresql://localhost:55432/labtimesheet (PostgreSQL 18.4)
Started LabtimesheetApplication in 4.068 seconds
Graceful shutdown complete
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env -u SPRING_PROFILES_ACTIVE -u LAB_SERVER_PORT -u LAB_FORWARD_HEADERS_STRATEGY -u LAB_DB_URL -u LAB_DB_USERNAME -u LAB_DB_PASSWORD -u LAB_SMTP_HOST -u LAB_SMTP_PORT -u LAB_PUBLIC_ORIGIN -u LAB_SECURITY_MASTER_KEY -u LAB_DEV_ENV_FILE /bin/zsh -lc 'export JAVA_HOME=/opt/homebrew/opt/openjdk@25; export PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH; export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock; ./mvnw test'

Tests run: 201, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS in 01:29 using PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

The committed example cannot prove another developer's local credentials or
an IDE working directory. Product SMTP and HolidayAPI revisions remain
Admin-console configuration and are intentionally absent from `.env`.
