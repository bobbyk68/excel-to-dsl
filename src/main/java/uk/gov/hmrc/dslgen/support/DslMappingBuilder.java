package uk.gov.hmrc.dslgen.support;

ipackage uk.gov.hmrc.rules.builder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DslBuilder {

    // Matches "left - right" with optional surrounding whitespace, but requires whitespace around '-'
    private static final Pattern SPLIT_ON_HYPHEN_WITH_SPACES = Pattern.compile("\\s+-\\s+");

    /**
     * Builds one or more DSL "[when]" RHS lines depending on the captured literal(s).
     * - If the literal is "A - B" (spaces around '-'), this returns two DSL lines, one per part.
     * - Otherwise, returns a single DSL line.
     */
    public List<String> buildWhenRhs(DslInput input) {
        // Example: input.dslTemplate() like "goodsitem with special procedure exists - with code equals {value}"
        // or "{1}" style placeholders — both supported by formatDsl(..)
        final String template = Objects.requireNonNull(input.dslTemplate(), "dslTemplate");
        final String literal  = Objects.requireNonNull(input.rightLiteral(), "rightLiteral").trim();

        // Heuristic: only split when there is whitespace around the hyphen. "A - B" → ["A", "B"]
        // "AA-123" or "A-B" remains a single literal.
        final String[] parts = SPLIT_ON_HYPHEN_WITH_SPACES.split(literal);

        if (parts.length > 1) {
            // multiple values → multiple lines, dedup & keep order
            return Arrays.stream(parts)
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .map(p -> formatDsl(template, p))
                    .distinct()
                    .collect(Collectors.toList());
        }

        // single value path
        return List.of(formatDsl(template, literal));
    }

    /**
     * Compatibility helper for legacy call sites that expect a single String.
     * Join with newline by default (customize the delimiter if needed).
     */
    public String buildWhenRhsJoined(DslInput input) {
        return String.join("\n", buildWhenRhs(input));
    }

    /**
     * Replaces either {value} or {1} placeholder conventions.
     * If neither placeholder exists, append the value at the end (defensive).
     */
    private String formatDsl(String template, String value) {
        if (template.contains("{value}")) {
            return template.replace("{value}", escape(value));
        }
        if (template.contains("{1}")) {
            return template.replace("{1}", escape(value));
        }
        // Defensive fallback: append a space + value to template
        return template + " " + escape(value);
    }

    /**
     * Minimal escaping hook in case the DSL has reserved characters.
     * Expand as your DSL requires (quotes, backslashes, etc.).
     */
    private String escape(String raw) {
        // Example: wrap in quotes if contains space (optional)
        // return raw.contains(" ") ? "'" + raw.replace("'", "\\'") + "'" : raw;
        return raw;
    }

    // Simple DTO for clarity (adjust to your actual AtomicHit / inputs)
    public static final class DslInput {
        private final String dslTemplate;
        private final String rightLiteral;

        public DslInput(String dslTemplate, String rightLiteral) {
            this.dslTemplate = dslTemplate;
            this.rightLiteral = rightLiteral;
        }

        public String dslTemplate() { return dslTemplate; }
        public String rightLiteral() { return rightLiteral; }
    }
}
