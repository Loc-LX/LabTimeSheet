package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/** Guards the reviewed, explicit reporting-library coordinates before report code consumes them. */
class ReportingDependencyContractTest {

    @Test
    void reportingLibrariesUseTheReviewedCoordinatesAndResolveOnTheTestClasspath() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom = factory.newDocumentBuilder().parse(Files.newInputStream(Path.of("pom.xml")));
        var xpath = XPathFactory.newInstance().newXPath();

        Map<String, String> expected = Map.of(
                "org.apache.poi:poi-ooxml", "5.5.1",
                "com.github.librepdf:openpdf-html", "3.0.3",
                "com.github.librepdf:openpdf-fonts-extra", "3.0.3");
        expected.forEach((coordinate, version) -> {
            String[] parts = coordinate.split(":", 2);
            try {
                String actual = xpath.evaluate(
                        "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']"
                                + "[*[local-name()='groupId']='" + parts[0] + "' and *[local-name()='artifactId']='"
                                + parts[1] + "']/*[local-name()='version']",
                        pom);
                assertThat(actual).as("version for %s", coordinate).isEqualTo(version);
            } catch (Exception failure) {
                throw new AssertionError("Could not inspect " + coordinate, failure);
            }
        });

        assertThat(Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook")).isNotNull();
        assertThat(Class.forName("org.openpdf.text.Document")).isNotNull();
    }
}
