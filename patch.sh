#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="rules-dslr-demo"
SRC_DIR="$ROOT_DIR/src"

if [[ ! -d "$SRC_DIR" ]]; then
  echo "Can't find $SRC_DIR. Run the original setup script first." >&2
  exit 1
fi

cat > "$SRC_DIR/EnglishQuantifier.java" <<'EOF'
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum EnglishQuantifier {
    EXISTS, FOR_ALL, NONE, EXACTLY, ATOMIC;

    private static final Map<String,Integer> WORD_TO_INT = Map.ofEntries(
        Map.entry("zero", 0),
        Map.entry("one", 1),
        Map.entry("single", 1),
        Map.entry("two", 2),
        Map.entry("three", 3),
        Map.entry("four", 4),
        Map.entry("five", 5),
        Map.entry("six", 6),
        Map.entry("seven", 7),
        Map.entry("eight", 8),
        Map.entry("nine", 9),
        Map.entry("ten", 10)
    );

    private static final Pattern EXACTLY_PATTERN =
        Pattern.compile("\\bexactly\\s+(\\d+|zero|one|single|two|three|four|five|six|seven|eight|nine|ten)\\b", Pattern.CASE_INSENSITIVE);

    public static EnglishQuantifier fromText(String english) {
        String s = (english == null ? "" : english).toLowerCase().trim();
        if (s.startsWith("there is at least one") || s.startsWith("at least one")) return EXISTS;
        if (s.startsWith("all ") || s.contains(" must all ") || s.contains(" must be all ")) return FOR_ALL;
        if (s.startsWith("none ") || s.contains(" must not ") || s.contains(" no ")) return NONE;
        if (s.startsWith("exactly")) return EXACTLY;
        return ATOMIC;
    }

    /** Supports "exactly 1", "exactly one", "exactly single", … */
    public static int extractExactlyN(String english) {
        if (english == null) throw new IllegalArgumentException("english is null");
        Matcher m = EXACTLY_PATTERN.matcher(english.toLowerCase());
        if (m.find()) {
            String token = m.group(1);
            if (token.chars().allMatch(Character::isDigit)) {
                return Integer.parseInt(token);
            }
            Integer n = WORD_TO_INT.get(token);
            if (n != null) return n;
        }
        // Fallback: naive token-based parse ("exactly X …")
        String[] t = english.toLowerCase().split("\\s+");
        if (t.length > 1 && "exactly".equals(t[0])) {
            String x = t[1];
            if (x.chars().allMatch(Character::isDigit)) return Integer.parseInt(x);
            Integer n = WORD_TO_INT.get(x);
            if (n != null) return n;
        }
        throw new IllegalArgumentException("Could not parse number for 'exactly' phrase: " + english);
    }
}
EOF

echo "Recompiling…"
javac "$SRC_DIR"/*.java

echo "Running demo…"
java -cp "$SRC_DIR" Demo
