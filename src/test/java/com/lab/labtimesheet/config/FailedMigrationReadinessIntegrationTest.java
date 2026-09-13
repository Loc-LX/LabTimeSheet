package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves that a failed schema migration stops the application rather than letting it serve.
 *
 * <p>Protects {@code ERR-007}, and depends on {@code ARC-007} keeping Flyway the sole schema
 * authority.
 */
class FailedMigrationReadinessIntegrationTest {

    /**
     * Protects {@code ERR-007}. Observable break: Flyway is made non-fatal, by disabling it, by
     * ignoring failed migrations, or by catching the failure during startup. The application then
     * comes up against a half-applied schema and answers requests with tables that are missing or
     * half-built, which is the one outcome the rule forbids.
     *
     * <p>The rule is satisfied twice over, and the test asserts both halves.
     *
     * <p>The first is startup. The fixture under {@code classpath:db/failing-migration} creates a
     * marker table and then runs a statement PostgreSQL rejects, and the context does not come up.
     * Nothing serves.
     *
     * <p>The second is the schema itself, and it is the half that only PostgreSQL provides. The
     * marker table is absent afterwards, because PostgreSQL applies DDL inside a transaction and
     * Flyway rolls the whole migration back on failure. There is therefore no partially migrated
     * schema to serve against, rather than one that merely goes unserved. Running this against an
     * in-memory database that commits DDL outside a transaction would assert the opposite and pass,
     * which is one concrete reason {@code ARC-003} requires PostgreSQL for a PostgreSQL-specific
     * rule.
     *
     * @throws Exception if the container connection cannot be opened
     */
    @Test
    void aFailedMigrationStopsTheContextInsteadOfServingAHalfMigratedSchema() throws Exception {
        try (PostgreSQLContainer postgres =
                new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"))) {
            postgres.start();

            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.datasource.url=" + postgres.getJdbcUrl(),
                            "spring.datasource.username=" + postgres.getUsername(),
                            "spring.datasource.password=" + postgres.getPassword(),
                            "spring.flyway.locations=classpath:db/failing-migration")
                    .run(context -> assertThat(context)
                            .as("ERR-007 requires a failed migration to stop startup")
                            .hasFailed());

            assertThat(tableExists(postgres, "err007_partial_marker"))
                    .as("PostgreSQL applies DDL transactionally, so the failed migration must leave "
                            + "no table behind and there is no partial schema to serve against")
                    .isFalse();
        }
    }

    private static boolean tableExists(PostgreSQLContainer postgres, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select count(*) from information_schema.tables "
                                + "where table_schema = 'public' and table_name = '" + table + "'")) {
            return rows.next() && rows.getInt(1) == 1;
        }
    }
}
