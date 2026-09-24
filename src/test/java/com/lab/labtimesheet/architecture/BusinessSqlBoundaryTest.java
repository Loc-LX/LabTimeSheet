package com.lab.labtimesheet.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BusinessSqlBoundaryTest {
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    // "@", then an optional package, so @Formula and @org.hibernate.annotations.Formula both match
    private static final String ANNOTATION = "@\\s*(?:\\w+\\s*\\.\\s*)*";
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\b(?:java\\.sql|javax\\.sql|org\\.springframework\\.jdbc)\\b"),
                    "direct SQL API"),
            new Rule(Pattern.compile("\\bcreateNative\\w*"), "createNative*"),
            new Rule(Pattern.compile("\\b(?:NativeQuery|doWork|doReturningWork)\\b"), "Hibernate native work"),
            new Rule(Pattern.compile(ANNOTATION + "(?:NativeQuery|NamedNativeQuery)\\b"), "native query annotation"),
            new Rule(Pattern.compile("\\bnativeQuery\\s*=\\s*true\\b"), "nativeQuery=true"),
            new Rule(Pattern.compile(ANNOTATION
                    + "(?:Formula|Subselect|SQLInsert|SQLUpdate|SQLDelete|SQLRestriction|ColumnTransformer)\\b"),
                    "Hibernate SQL fragment annotation"));

    /**
     * Protects {@code ARC-006}, {@code D18} and {@code D42}.
     *
     * <p>The observable break is direct SQL entry points in production Java code, including repositories;
     * comments are ignored, and a current native-query call is expected to make this RED run fail once.</p>
     */
    @Test
    void productionJavaDoesNotUseDirectSqlEntryPoints() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var sources = Files.walk(PRODUCTION_SOURCES)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> violations.addAll(findViolations(path)));
        }

        assertThat(violations).isEmpty();
    }

    private static List<String> findViolations(Path source) {
        String code;
        try {
            code = withoutComments(Files.readString(source));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
        }

        List<String> violations = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(code);
            while (matcher.find()) {
                int line = 1;
                for (int index = 0; index < matcher.start(); index++) {
                    if (code.charAt(index) == '\n') {
                        line++;
                    }
                }
                violations.add(source.getFileName() + ":" + line + " " + rule.label());
            }
        }
        return violations;
    }

    private static String withoutComments(String source) {
        StringBuilder code = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean stringLiteral = false;
        boolean characterLiteral = false;
        boolean textBlock = false;
        boolean escaped = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    code.append(current);
                } else {
                    code.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    code.append("  ");
                    index++;
                } else {
                    code.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (textBlock) {
                code.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (source.startsWith("\"\"\"", index)) {
                    code.append("\"\"");
                    index += 2;
                    textBlock = false;
                }
                continue;
            }
            if (stringLiteral || characterLiteral) {
                code.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if ((stringLiteral && current == '"') || (characterLiteral && current == '\'')) {
                    stringLiteral = false;
                    characterLiteral = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                lineComment = true;
                code.append("  ");
                index++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                code.append("  ");
                index++;
            } else if (source.startsWith("\"\"\"", index)) {
                textBlock = true;
                code.append("\"\"\"");
                index += 2;
            } else {
                code.append(current);
                if (current == '"') {
                    stringLiteral = true;
                } else if (current == '\'') {
                    characterLiteral = true;
                }
            }
        }
        return code.toString();
    }

    private record Rule(Pattern pattern, String label) {}
}
