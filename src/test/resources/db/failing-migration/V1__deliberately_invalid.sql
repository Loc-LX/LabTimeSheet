-- Fixture for ERR-007. Not a migration this application ever runs.
--
-- Flyway's default location is classpath:db/migration; this file sits under
-- classpath:db/failing-migration and is reached only when a test points
-- spring.flyway.locations at it deliberately.
--
-- The first statement succeeds and the second does not. On PostgreSQL both are
-- inside one transaction, so the failure rolls the marker table back as well.
-- The test asserts that: a failed migration leaves no schema behind at all, and
-- the context that would have served it does not start.
CREATE TABLE err007_partial_marker (id BIGINT PRIMARY KEY);

THIS IS NOT VALID SQL AND FLYWAY MUST FAIL HERE;
