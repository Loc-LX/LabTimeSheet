# Test Evidence: reviewed reporting dependency coordinates

- **Test type:** Unit
- **Requirement IDs:** `RPT-007`, `ARC-002`, `OPS-019`
- **Scenario IDs:** `AC-RPT-003`
- **Test class/method:** `com.lab.labtimesheet.config.ReportingDependencyContractTest.reportingLibrariesUseTheReviewedCoordinatesAndResolveOnTheTestClasspath`
- **Implementation commit:** `39fc97b84cabc6b6d4669e13a5521eeec3e191ce`

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
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests package
```

```text
2026-08-22T14:09:10+07:00 — exact implementation tree 39fc97b84cabc6b6d4669e13a5521eeec3e191ce;
Java 25 compile/test-compile, WAR packaging, and Spring Boot repackaging completed; BUILD SUCCESS.
```

The dependency-only milestone has no application behavior to widen. The focused contract and dependency tree above
remain the direct coordinate checks; this fresh package is the affected compile/package gate and the complete platform
suite remains the branch-completion gate.

## External-test boundaries

This evidence does not prove XLSX/PDF content, Vietnamese font embedding, browser download behavior, licensing output,
or report filter/total parity. `openpdf-fonts-extra` supplies font resources but does not by itself embed a Unicode
font in a generated PDF; the Reports/UI owner must explicitly register/embed a TTF and prove Vietnamese output under
`RPT-007`/`AC-RPT-003`.
