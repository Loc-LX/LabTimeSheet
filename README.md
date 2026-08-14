# Lab Timesheet

## Local development

The application targets Java 25 and expects PostgreSQL on
`localhost:55432` when the default `dev` profile is active. Override any local
value with `LAB_DB_URL`, `LAB_DB_USERNAME`, `LAB_DB_PASSWORD`,
`LAB_SMTP_HOST`, or `LAB_SMTP_PORT`.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

Tests use PostgreSQL 18.4 through Testcontainers and do not use the developer
database:

```bash
./mvnw test
```

Every feature test must have a companion Markdown evidence record under
[`docs/tests`](docs/tests/README.md).
