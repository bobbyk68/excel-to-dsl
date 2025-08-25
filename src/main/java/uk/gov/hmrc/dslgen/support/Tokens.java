package uk.gov.hmrc.dslgen.support;

import java.util.List;
import java.util.stream.Collectors;

public final class Tokens {
    private Tokens() {}

    /** Join values as CSV with each value quoted and safely escaped. -> "A","D" */
    public static String quoteEachCsv(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream()
                .map(Tokens::quote)
                .collect(Collectors.joining(","));
    }

    /** Quote and escape a single string for use in DSL/Java. */
    public static String quote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
