package uk.gov.hmrc.dslgen.dict;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexes existing Drools DSL files so we can validate against them.
 * Supports entries like: [when] some phrase = $dec : Declaration( ... )
 */
public class DslIndex {

    private static final Pattern ENTRY = Pattern.compile(
            "^\\s*\\[(when|then)\\]\\s*(.*?)\\s*=\\s*(.*?)\\s*$"
    );

    private final Set<String> whenLhs = new HashSet<>();
    private final Set<String> thenLhs = new HashSet<>();
    private final Set<String> whenRhs = new HashSet<>();
    private final Set<String> thenRhs = new HashSet<>();

    /** Load all *.dsl files under rootDir (recursively). */
    public static DslIndex load(Path rootDir) {
        DslIndex idx = new DslIndex();
        if (rootDir == null) return idx;
        try {
            if (!Files.exists(rootDir)) return idx;
            try (var stream = Files.walk(rootDir)) {
                stream.filter(p -> p.toString().endsWith(".dsl"))
                      .forEach(idx::parseFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk DSL directory: " + rootDir, e);
        }
        return idx;
    }

    private void parseFile(Path file) {
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
                Matcher m = ENTRY.matcher(trimmed);
                if (!m.matches()) continue;

                String section = m.group(1).toLowerCase(); // when|then
                String lhs = normalize(m.group(2));
                String rhs = normalize(m.group(3));

                if ("when".equals(section)) {
                    whenLhs.add(lhs);
                    whenRhs.add(rhs);
                } else {
                    thenLhs.add(lhs);
                    thenRhs.add(rhs);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read DSL file: " + file, e);
        }
    }

    /** Normalise whitespace & trim for comparisons. */
    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    // Expose sets (read-only copies)
    public Set<String> getWhenLhs() { return Set.copyOf(whenLhs); }
    public Set<String> getWhenRhs() { return Set.copyOf(whenRhs); }
    public Set<String> getThenLhs() { return Set.copyOf(thenLhs); }
    public Set<String> getThenRhs() { return Set.copyOf(thenRhs); }
}
