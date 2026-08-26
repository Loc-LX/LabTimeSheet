# Test Evidence: Task effort-planning catalog

- **Test type:** Integration
- **Requirement IDs:** DB-013
- **Scenario IDs:** DB-013 catalog extension (no pre-existing AC-DB-003 is invented)
- **Test class/method:** `com.lab.labtimesheet.config.PlatformFoundationTest.flywayCreatesApprovedPostgresCatalog`
- **Implementation commit:** pending

## Protected behavior

Flyway must extend the V1 PostgreSQL catalog with a nullable, unbackfilled Task estimate and an append-only Remaining effort forecast table. The catalog test checks the table/FK counts, estimate column, forecast table, named constraints, and lookup indexes against a real PostgreSQL 18.4 Testcontainer.

## Test method

The existing public PlatformFoundationTest catalog seam starts the real Spring/Flyway/Testcontainers boundary and independently queries PostgreSQL information-schema and catalog metadata. The test now expects 24 application tables and 61 foreign-key constraints, verifies the integer/nullable/no-default `tasks.estimated_minutes` column, the forecast table, six named checks/linearity constraints, all five forecast FK names, the append-only trigger, and five forecast indexes covering lookup and foreign-key paths.

## Hand-derived expected result

V2 adds one table and five FK constraints (the Task/project and two membership relationships are composite PostgreSQL constraints, plus project and supersession); therefore the V1 totals 23 tables/56 FKs become 24 tables/61 FKs. Existing policy seed assertions remain unchanged.

## RED

**Command**

```text
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'
& 'C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -q '-Dtest=PlatformFoundationTest#flywayCreatesApprovedPostgresCatalog' '-Duser.timezone=Asia/Ho_Chi_Minh' clean test
```

**Observed result**

Java 25 + cached Maven 3.9.16 + `-Duser.timezone=Asia/Ho_Chi_Minh` + `clean test`, with V2 temporarily withheld, ran against PostgreSQL 18.4 and Flyway V1. It failed for the intended missing-migration reason: `expected: 24 but was: 23`; 1 test, 1 failure, 0 errors.

## GREEN

**Command**

```text
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'
& 'C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -q '-Dtest=PlatformFoundationTest#flywayCreatesApprovedPostgresCatalog' '-Duser.timezone=Asia/Ho_Chi_Minh' clean test
```

**Observed result**

Java 25 + cached Maven 3.9.16 + `-Duser.timezone=Asia/Ho_Chi_Minh` + `clean test` ran against PostgreSQL 18.4; Flyway validated and applied migrations through v2 and exited 0. The focused catalog test passed: 1 test, 0 failures, 0 errors, 0 skipped. Maven exited 0.

## Affected suite

**Command and result**

```text
The focused PlatformFoundationTest catalog command passed against PostgreSQL 18.4. The full Maven suite remains outside this packet and retains the known independent `Asia/Saigon` and Windows `LayerStructureTest` failures.
```

## External-test boundaries

This catalog seam does not prove application estimate authorization, work-log locking, forecast transaction atomicity, correction timing, report rendering, or append-only behavior through application services. Those belong to later Task and Reporting packets. A V1 database containing a pre-existing Task was not separately upgraded in this focused test; nullable/no-default catalog metadata plus SQL review support the no-backfill design, but an upgrade fixture is not proven. It also does not repair or mask the known independent `Asia/Saigon` and Windows `LayerStructureTest` baseline failures.
