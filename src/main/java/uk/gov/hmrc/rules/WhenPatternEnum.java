// v2
package uk.gov.hmrc.rules.when;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Each enum can match ANY of its regexes; first match wins, enum order defines precedence. */
public enum WhenPatternEnum {

    // 1) Same render, multiple ways users might write it
    EXPIRY_BEFORE_TODAY(List.of(
            "(?i)^license\\s+expiry\\s*<\\s*today$",
            "(?i)^expiry\\s*<\\s*today$"
    )) {
        @Override public String render(Matcher m) {
            return "license: License(expiry != null && expiry < $today)";
        }
    },

    // 2) Two phrasings for equality on type
    TYPE_EQUALS(List.of(
            "(?i)^type\\s*==\\s*([A-Za-z0-9_]+)$",
            "(?i)^license\\s+type\\s+is\\s+([A-Za-z0-9_]+)$"
    )) {
        @Override public String render(Matcher m) {
            String type = m.group(1);
            return "license: License(type == \"" + type + "\")";
        }
    },

    ACTIVE_TRUE(List.of(
            "(?i)^active$",
            "(?i)^license\\s+is\\s+active$"
    )) {
        @Override public String render(Matcher m) {
            return "license: License(active == true)";
        }
    };

    private final List<Pattern> patterns;

    WhenPatternEnum(List<String> regexes) {
        this.patterns = regexes.stream().map(Pattern::compile).toList();
    }

    /** Try each pattern; return the first matching Matcher or null. */
    public Matcher tryMatch(String input) {
        if (input == null) return null;
        String s = input.trim();
        for (var p : patterns) {
            var m = p.matcher(s);
            if (m.matches()) return m;
        }
        return null;
    }

    /** Produce the final LHS/DSL line using the winning pattern’s matcher. */
    public abstract String render(Matcher m);
}