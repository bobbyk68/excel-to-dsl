package uk.gov.hmrc.dslgen.support;

public final class DslErrorConfig {

    public static final class PatternRule {
        private final String match;     // lowercase needle
        private final String tag;       // label to add on match
        private final String type;      // "prefix" or "contains"

        public PatternRule(String match, String tag, String type) {
            this.match = match;
            this.tag = tag;
            this.type = (type == null || type.isBlank()) ? "prefix" : type.toLowerCase();
        }

        public boolean matches(String lineLower) {
            if ("contains".equals(type)) {
                return lineLower.contains(match);
            }
            // default: "prefix"
            return lineLower.startsWith(match);
        }

        public String tag() { return tag; }
    }

    private final String joinWord;
    private final String prefix;
    private final java.util.List<PatternRule> rules;

    private DslErrorConfig(String joinWord, String prefix, java.util.List<PatternRule> rules) {
        this.joinWord = (joinWord == null || joinWord.isBlank()) ? "and" : joinWord.trim();
        this.prefix = (prefix == null) ? "validation error for " : normalizePrefix(prefix);
        this.rules = rules == null ? java.util.List.of() : java.util.List.copyOf(rules);
    }

    public String joinWord() { return joinWord; }
    public String prefix() { return prefix; }
    public java.util.List<PatternRule> rules() { return rules; }

    // ---- loading ----

    private static volatile DslErrorConfig INSTANCE;

    public static DslErrorConfig get() {
        DslErrorConfig ref = INSTANCE;
        if (ref != null) return ref;
        synchronized (DslErrorConfig.class) {
            if (INSTANCE == null) {
                INSTANCE = loadFromClasspath("/dsl-error-tags.yaml");
            }
            return INSTANCE;
        }
    }

    private static DslErrorConfig loadFromClasspath(String resourcePath) {
        try (java.io.InputStream in = DslErrorConfig.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return defaults(); // no file → defaults
            }
            java.util.List<String> lines = readAllLines(in);
            return parseSimpleYaml(lines);
        } catch (Exception ex) {
            // On any parse/read issue, use defaults (fail-safe)
            return defaults();
        }
    }

    // Very small, dependency-free YAML subset parser:
    // Supports:
    //   joinWord: <value>
    //   prefix: <value>
    //   patterns:
    //     - match: <value>
    //       tag: <value>
    //       type: <value>
    private static DslErrorConfig parseSimpleYaml(java.util.List<String> rawLines) {
        String joinWord = null;
        String prefix = null;
        java.util.List<PatternRule> rules = new java.util.ArrayList<>();

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String s : rawLines) {
            String t = s;
            int hash = t.indexOf('#');
            if (hash >= 0) t = t.substring(0, hash);
            t = t.replace("\t", "    ").trim();
            if (!t.isEmpty()) lines.add(t);
        }

        boolean inPatterns = false;
        java.util.Map<String, String> current = null;

        for (String line : lines) {
            if (line.startsWith("joinWord:")) {
                joinWord = valueOf(line);
                continue;
            }
            if (line.startsWith("prefix:")) {
                prefix = valueOf(line);
                continue;
            }
            if (line.startsWith("patterns:")) {
                inPatterns = true;
                continue;
            }
            if (inPatterns) {
                if (line.startsWith("- ")) {
                    // flush previous
                    if (current != null) {
                        rules.add(toRule(current));
                    }
                    current = new java.util.HashMap<>();
                    // support inline "- match: foo" short form
                    String afterDash = line.substring(2).trim();
                    if (afterDash.startsWith("match:")) {
                        current.put("match", valueOf(afterDash));
                    } else if (!afterDash.isEmpty()) {
                        // Not supporting arbitrary inline formats; ignore
                    }
                    continue;
                }
                if (line.startsWith("match:") && current != null) {
                    current.put("match", valueOf(line));
                    continue;
                }
                if (line.startsWith("tag:") && current != null) {
                    current.put("tag", valueOf(line));
                    continue;
                }
                if (line.startsWith("type:") && current != null) {
                    current.put("type", valueOf(line));
                    continue;
                }
            }
        }
        if (current != null) {
            rules.add(toRule(current));
        }

        // normalize & lowercase matches
        java.util.List<PatternRule> normalized = new java.util.ArrayList<>();
        for (PatternRule r : rules) {
            if (r == null) continue;
            if (r.tag == null || r.tag.isBlank()) continue;
            String m = r.match == null ? "" : r.match.trim().toLowerCase();
            if (m.isEmpty()) continue;
            normalized.add(new PatternRule(m, r.tag.trim(), r.type));
        }

        if (normalized.isEmpty()) {
            return defaults(); // if patterns missing → defaults
        }
        return new DslErrorConfig(joinWord, prefix, normalized);
    }

    private static String valueOf(String line) {
        int idx = line.indexOf(':');
        if (idx < 0 || idx == line.length() - 1) return "";
        String val = line.substring(idx + 1).trim();
        // strip surrounding quotes if present
        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    private static PatternRule toRule(java.util.Map<String, String> m) {
        String match = m.get("match");
        String tag = m.get("tag");
        String type = m.get("type");
        if (match == null || tag == null) return null;
        return new PatternRule(match, tag, type);
    }

    private static java.util.List<String> readAllLines(java.io.InputStream in) throws java.io.IOException {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in));
        java.util.List<String> out = new java.util.ArrayList<>();
        String s;
        while ((s = br.readLine()) != null) out.add(s);
        return out;
    }

    private static String normalizePrefix(String p) {
        String t = p.trim();
        // Ensure trailing space for natural reading: "validation error for "
        if (!t.endsWith(" ")) t = t + " ";
        return t;
    }

    // Safe defaults if YAML is missing or broken
    private static DslErrorConfig defaults() {
        java.util.List<PatternRule> rules = java.util.List.of(
            new PatternRule("goods item with special procedure", "special procedure", "prefix"),
            new PatternRule("no matching goods item with additional information", "no additional information", "prefix")
        );
        return new DslErrorConfig("and", "validation error for ", rules);
    }
}
