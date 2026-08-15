# Test Evidence: Platform production API Javadocs

- **Test type:** Unit (documentation/static verification)
- **Requirement IDs:** Repository Javadoc implementation standard; Iteration 1 retrofit exception
- **Scenario IDs:** No runtime acceptance-scenario mapping
- **Test class/method:** Maven Javadoc Plugin 3.12.0 over Platform production sources
- **Implementation commit:** `8ff6ee3d873db909b1ce9df690f7a3abb2c3c79d`

## Protected behavior

Platform-owned production types and declared public/protected non-trivial APIs under the root application package,
`config`, `feature.account`, and `feature.integration` describe their business purpose and important authorization,
transaction, state-transition, time, persistence, encryption, and raw-token boundaries. Trivial form/entity accessors
remain intentionally undocumented as permitted by the repository standard.

## Test method

The Maven Javadoc Plugin generates protected/public API documentation using Java 25 with doclint enabled. The
`missing` category is disabled because the repository explicitly exempts trivial accessors and generated methods;
all structural HTML/reference/syntax categories remain enabled. Compilation and the full runtime suite separately
verify the documented sources.

## Hand-derived expected result

Documentation generation completes without doclint errors or warnings for the selected categories, and Java
compilation plus all Platform tests remain green.

## RED

**Command**

```text
Not applicable: this is the approved Iteration 1 documentation retrofit. No runtime RED was invented.
```

**Observed result**

```text
Before the retrofit, manual source audit found missing type and non-trivial API Javadocs throughout Platform-owned
config, account, and integration code. This is review evidence, not a claimed executable RED.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests -Dshow=protected -Ddoclint=all,-missing javadoc:javadoc
```

**Observed result**

```text
Maven Javadoc Plugin 3.12.0
BUILD SUCCESS
No Javadoc warnings were emitted.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## External-test boundaries

Generated Javadocs validate documentation syntax and references, not whether every statement is behaviorally true.
The focused and full production-shaped tests provide that separate runtime evidence. Private fields/helpers and
trivial accessors are outside the retrofit contract.
