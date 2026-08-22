# Test Evidence: reviewed reporting dependency coordinates

- **Test type:** Unit
- **Requirement IDs:** `OPS-001`, `OPS-009`
- **Scenario IDs:** `AC-OPS-004`
- **Test class/method:** `com.lab.labtimesheet.config.ReportingDependencyContractTest.reportingLibrariesUseTheReviewedCoordinatesAndResolveOnTheTestClasspath`
- **Implementation commit:** `pending`

## Protected behavior

The Platform Maven boundary pins the three reviewed Iteration 3 report-export dependencies exactly: Apache POI
OOXML 5.5.1 and OpenPDF HTML/fonts-extra 3.0.3. The contract also proves representative POI and OpenPDF classes
resolve on the test classpath before any report implementation consumes them.

## Test method

The test parses the repository POM with a namespace-independent XPath expression, asserts each exact group/artifact
version, and loads representative classes from the resolved dependencies. It is intentionally limited to dependency
coordinates and classpath resolution; it does not implement or test report output.

## Hand-derived expected result

The POM must contain `org.apache.poi:poi-ooxml:5.5.1`,
`com.github.librepdf:openpdf-html:3.0.3`, and
`com.github.librepdf:openpdf-fonts-extra:3.0.3`. POI's `XSSFWorkbook` and OpenPDF 3's `org.openpdf.text.Document`
must load successfully.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ReportingDependencyContractTest test
```

**Observed result**

```text
2026-08-22T13:46:53+07:00 — Tests run: 1, Failures: 1, Errors: 0; the POM had no exact openpdf-html 3.0.3
coordinate. BUILD FAILURE. This was the expected missing dependency-contract RED.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=ReportingDependencyContractTest test
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw dependency:tree -Dincludes=org.apache.poi:poi-ooxml,com.github.librepdf:openpdf-html,com.github.librepdf:openpdf-fonts-extra
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests compile
```

**Observed result**

```text
2026-08-22T13:47:34+07:00 — focused contract: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
2026-08-22T13:47:52+07:00 — dependency tree resolved all three exact coordinates; BUILD SUCCESS.
2026-08-22T13:47:58+07:00 — Java 25 compile; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
The dependency-only milestone has no application behavior to widen. The focused contract, dependency tree, and
production compile above are the affected checks; the complete platform suite remains the branch-completion gate.
```

## External-test boundaries

This evidence does not prove XLSX/PDF content, Vietnamese font embedding, browser download behavior, licensing output,
or report filter/total parity. Those remain the Reports/UI owner's implementation and integration scope.
