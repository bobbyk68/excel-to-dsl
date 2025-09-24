#!/usr/bin/env bash
set -euo pipefail

ROOT="dsl-dslr-hyphen-demo"
SRC="$ROOT/src"
OUT="$ROOT/build"
mkdir -p "$SRC" "$OUT"

cat > "$SRC/RuleRow.java" <<'EOF'
public class RuleRow {
    private final String id;
    private final String ifCondition;    // Excel IF (English)
    private final String thenCondition;  // Excel THEN (English)
    public RuleRow(String id, String ifCondition, String thenCondition) {
        this.id = id; this.ifCondition = ifCondition; this.thenCondition = thenCondition;
    }
    public String id() { return id; }
    public String ifCondition() { return ifCondition; }
    public String thenCondition() { return thenCondition; }
}
EOF

cat > "$SRC/AtomicHit.java" <<'EOF'
import java.util.List;
public class AtomicHit {
    final String template;     // canonical English (may contain " - ") with placeholders {1}
    final String concrete;     // English with values substituted (used for DSLR)
    final java.util.List<String> groups; // captured values, 1-based in template -> index 0 here
    final boolean isIf;        // role: IF (true) / THEN (false)
    public AtomicHit(String template, String concrete, java.util.List<String> groups, boolean isIf) {
        this.template = template; this.concrete = concrete; this.groups = groups; this.isIf = isIf;
    }
}
EOF

cat > "$SRC/ConstraintCase.java" <<'EOF'
public enum ConstraintCase {
    ALL_OF, AT_LEAST_ONE, NONE, EXACTLY_ONE, EXISTS,
    // legacy aliases if you still emit these somewhere
    ONE_OF, ANY_OF, NONE_OF;
    public static ConstraintCase canonical(ConstraintCase k) {
        return switch (k) {
            case ONE_OF -> EXACTLY_ONE;
            case ANY_OF -> AT_LEAST_ONE;
            case NONE_OF -> NONE;
            default -> k;
        };
    }
    public boolean requiresFlip() { return canonical(this) == ALL_OF; }
    public static ConstraintCase fromEnglish(String thenEnglish) {
        if (thenEnglish == null) return EXISTS;
        String s = thenEnglish.toLowerCase().trim();
        if (s.startsWith("all ")) return ALL_OF;
        if (s.startsWith("at least one") || s.startsWith("any ")) return AT_LEAST_ONE;
        if (s.startsWith("none")) return NONE;
        if (s.startsWith("exactly one") || s.startsWith("exactly 1") || s.startsWith("one of")) return EXACTLY_ONE;
        return EXISTS;
    }
}
EOF

cat > "$SRC/PatternEntry.java" <<'EOF'
import java.util.regex.*;
import java.util.*;
public class PatternEntry {
    public final String id;
    public final Pattern pattern;     // regex to match Excel English
    public final String dslTemplate;  // canonical English with placeholders {1}
    public final boolean forIf;       // which side this entry is intended for (IF/THEN)
    public PatternEntry(String id, String regex, String dslTemplate, boolean forIf) {
        this.id = id; this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE); this.dslTemplate = dslTemplate; this.forIf = forIf;
    }
    public AtomicHit tryMatch(String english, boolean isIf) {
        Matcher m = pattern.matcher(english);
        if (!m.matches()) return null;
        java.util.List<String> groups = new ArrayList<>();
        for (int i = 1; i <= m.groupCount(); i++) groups.add(m.group(i));
        String concrete = dslTemplate;
        for (int i = 1; i <= m.groupCount(); i++) {
            concrete = concrete.replace("{"+i+"}", m.group(i));
        }
        return new AtomicHit(dslTemplate, concrete, groups, isIf);
    }
}
EOF

cat > "$SRC/PatternLibrary.java" <<'EOF'
import java.util.*;
public class PatternLibrary {
    private static final List<PatternEntry> ENTRIES = List.of(
        // IF (two-part; will become two DSLR lines)
        new PatternEntry(
            "IF_SP_EQUALS",
            "\\s*at\\s+least\\s+one\\s+GoodsItem\\s+with\\s+special\\s+procedure\\s+code\\s+equals\\s+(.+)\\s*",
            "at least one GoodsItem with special procedure exists - with type code equals {1}",
            true
        ),
        // THEN ALL_OF (two-part; violator on dash)
        new PatternEntry(
            "THEN_ALL_LTE",
            "\\s*all\\s+goodsitem\\.invoiceAmount\\.value\\s+is\\s+less\\s+than\\s+or\\s+equal\\s+to\\s+(.+)\\s*",
            "all goodsitem.invoiceAmount.value is less than or equal to {1} - any value greater than {1}",
            false
        ),
        // THEN AT_LEAST_ONE (single line)
        new PatternEntry(
            "THEN_AT_LEAST_ONE_DOC",
            "\\s*at\\s+least\\s+one\\s+GoodsItem\\s+additionalDocument\\s+type\\s+code\\s+equals\\s+(.+)\\s*",
            "at least one GoodsItem additionalDocument type code equals {1}",
            false
        ),
        // THEN NONE (single line)
        new PatternEntry(
            "THEN_NONE_TYPE",
            "\\s*none\\s+GoodsItem\\s+type\\s+code\\s+equals\\s+(.+)\\s*",
            "none GoodsItem type code equals {1}",
            false
        ),
        // THEN EXACTLY_ONE (single line)
        new PatternEntry(
            "THEN_EXACTLY_ONE_DOC",
            "\\s*exactly\\s+one\\s+GoodsItem\\s+additionalDocument\\s+type\\s+code\\s+equals\\s+(.+)\\s*",
            "exactly one GoodsItem additionalDocument type code equals {1}",
            false
        )
    );
    public static AtomicHit findMatch(String english, boolean isIf) {
        for (PatternEntry e : ENTRIES) {
            if (e.forIf != isIf) continue;
            AtomicHit h = e.tryMatch(english, isIf);
            if (h != null) return h;
        }
        throw new IllegalArgumentException("No pattern matched: " + (isIf ? "IF: " : "THEN: ") + english);
    }
}
EOF

cat > "$SRC/DslrWriter.java" <<'EOF'
import java.io.*;
public class DslrWriter implements Closeable {
    private final BufferedWriter bw;
    private final String path;
    public DslrWriter(String path) throws IOException { this.path = path; this.bw = new BufferedWriter(new FileWriter(path, false)); }
    public void beginRule(String id) throws IOException { bw.write("rule \"" + id + "\"\nwhen\n"); }
    // write one or two lines depending on presence of " - "
    public void whenFromEnglish(String englishConcrete) throws IOException {
        String[] parts = englishConcrete.split("\\s+-\\s+", 2);
        if (parts.length == 1) {
            bw.write("  " + englishConcrete + "\n");
        } else {
            bw.write("  " + parts[0].trim() + "\n");
            bw.write("    - " + parts[1].trim() + "\n");
        }
    }
    public void endRule() throws IOException { bw.write("then\n  // TODO RHS\nend\n\n"); }
    @Override public void close() throws IOException { bw.flush(); bw.close(); }
    public String path() { return path; }
}
EOF

cat > "$SRC/DslWriter.java" <<'EOF'
import java.io.*;
import java.util.*;

public class DslWriter implements Closeable {
    private final BufferedWriter bw;
    private final String path;
    private final Set<String> emitted = new LinkedHashSet<>();
    public DslWriter(String path) throws IOException { this.path = path; this.bw = new BufferedWriter(new FileWriter(path, false)); }
    public String path() { return path; }

    public void emitEntries(String englishTemplate, boolean isIf, ConstraintCase kase) throws IOException {
        // De-dupe by the full english key (anchor or dash is part of the key)
        String[] parts = englishTemplate.split("\\s+-\\s+", 2);
        if (parts.length == 1) {
            String key = parts[0].trim();
            if (emitted.add(key)) {
                bw.write("[when] " + key + " =\n");
                for (String line : singleLineDrools(key, isIf, kase)) bw.write("    " + line + "\n");
                bw.write("\n");
            }
            return;
        }
        String anchor = parts[0].trim();
        String dash   = parts[1].trim();

        // 1) anchor entry → binder only (no flip)
        if (emitted.add(anchor)) {
            bw.write("[when] " + anchor + " =\n");
            for (String line : anchorDrools(anchor, isIf)) bw.write("    " + line + "\n");
            bw.write("\n");
        }
        // 2) dash entry → predicate (flip only for THEN + ALL_OF)
        String dashKey = "- " + dash;
        if (emitted.add(dashKey)) {
            bw.write("[when] " + dashKey + " =\n");
            for (String line : dashDrools(dash, isIf, ConstraintCase.canonical(kase) == ConstraintCase.ALL_OF && !isIf))
                bw.write("    " + line + "\n");
            bw.write("\n");
        }
    }

    // ------- drools builders (minimal, example-focused; replace with your registry-driven emitters) -------

    private List<String> anchorDrools(String anchorEnglish, boolean isIf) {
        String var = isIf ? "$if" : "$then";
        // Select a fact type from the wording
        String fact = anchorEnglish.toLowerCase().contains("additionaldocument") ? "GoodsItemDocumentFact" : "GoodsItemFacts";
        return List.of(var + " : " + fact + "( $seq : sequence )");
    }

    private List<String> dashDrools(String dashEnglish, boolean isIf, boolean reverseForThenAll) {
        // Map wording to field + operator. In a real system, resolve from a registry.
        String fact = dashEnglish.toLowerCase().contains("type code") ? "GoodsItemFacts" : "GoodsItemFacts";
        String var  = isIf ? "$if" : "$then";

        if (dashEnglish.toLowerCase().contains("type code equals {1}")) {
            return List.of(
                fact + "( this == " + var + ", typeCode != null, typeCode == \"{1}\" )"
            );
        }
        if (dashEnglish.toLowerCase().contains("any value greater than {1}")) {
            // THEN universal violator: > 0 compare
            return List.of(
                "GoodsItemFacts( this == " + var + ",",
                "                invoiceAmount != null,",
                "                invoiceAmount.value != null,",
                "                invoiceAmount.value.compareTo(new java.math.BigDecimal(\"{1}\")) > 0 )"
            );
        }
        // fallback
        return List.of(fact + "( this == " + var + ", field != null, field == \"{1}\" )");
    }

    private List<String> singleLineDrools(String english, boolean isIf, ConstraintCase kase) {
        String low = english.toLowerCase();
        ConstraintCase canon = ConstraintCase.canonical(kase);

        if (canon == ConstraintCase.AT_LEAST_ONE || canon == ConstraintCase.NONE) {
            // not(...)
            if (low.contains("additionaldocument") || low.contains("type code"))
                return List.of("not( GoodsItemDocumentFact( typeCode != null, typeCode == \"{1}\" ) )");
            return List.of("not( GoodsItemFacts( field != null, field == \"{1}\" ) )");
        }
        if (canon == ConstraintCase.EXACTLY_ONE) {
            if (low.contains("additionaldocument") || low.contains("type code"))
                return List.of(
                    "accumulate(",
                    "  GoodsItemDocumentFact( typeCode != null, typeCode == \"{1}\" ),",
                    "  $cnt : count(1)",
                    ") and eval( $cnt != 1 )"
                );
            return List.of(
                "accumulate(",
                "  GoodsItemFacts( field != null, field == \"{1}\" ),",
                "  $cnt : count(1)",
                ") and eval( $cnt != 1 )"
            );
        }
        // default: plain single-line
        return List.of("GoodsItemFacts( field != null, field == \"{1}\" )");
    }

    @Override public void close() throws IOException { bw.flush(); bw.close(); }
}
EOF

cat > "$SRC/HyphenTwoPhaseComposer.java" <<'EOF'
import java.util.*;
public class HyphenTwoPhaseComposer {
    public static final class Result {
        public final java.util.List<String> dslrLines;    // English lines for DSLR (1 or 2)
        public final java.util.List<String> dslKeys;      // English keys for DSL (one or two: anchor & "- dash")
        public Result(List<String> dslrLines, List<String> dslKeys) {
            this.dslrLines = dslrLines; this.dslKeys = dslKeys;
        }
    }
    // Split once; English stays as requirement. No flipping here (flip is encoded in DSL drools mapping).
    public Result composeEnglish(String englishTemplateWithPlaceholders, java.util.List<String> groups) {
        String concrete = bind(englishTemplateWithPlaceholders, groups);
        String[] parts = concrete.split("\\s+-\\s+", 2);
        if (parts.length == 1) {
            return new Result(List.of(concrete), List.of(parts[0].trim()));
        }
        String anchor = parts[0].trim();
        String dash   = parts[1].trim();
        return new Result(List.of(anchor, "- " + dash), List.of(anchor, "- " + dash));
    }
    private String bind(String template, java.util.List<String> groups) {
        String out = template;
        for (int i = 0; i < groups.size(); i++) {
            String g = groups.get(i);
            out = out.replace("{"+(i+1)+"}", g);
        }
        return out;
    }
}
EOF

cat > "$SRC/WhenPatternMatcher.java" <<'EOF'
import java.io.*;
import java.util.*;

public class WhenPatternMatcher implements Closeable {
    private final DslWriter dsl;
    private final DslrWriter dslr;
    private final HyphenTwoPhaseComposer composer = new HyphenTwoPhaseComposer();

    public WhenPatternMatcher(String dslPath, String dslrPath) throws IOException {
        this.dsl = new DslWriter(dslPath);
        this.dslr = new DslrWriter(dslrPath);
    }

    public void collectAll(java.util.List<RuleRow> rows) throws IOException {
        for (RuleRow row : rows) {
            // PASS 1: match both IF and THEN against the pattern library (this stands in for your JSON)
            AtomicHit ifHit   = PatternLibrary.findMatch(row.ifCondition(), true);
            AtomicHit thenHit = PatternLibrary.findMatch(row.thenCondition(), false);

            // Derive THEN case (used only for DSL drools mapping decisions, not for DSLR English)
            ConstraintCase kase = ConstraintCase.fromEnglish(thenHit.template);

            // Start rule in DSLR
            dslr.beginRule(row.id());

            // IF side: compose English (two lines if dash present), write DSLR, and emit DSL entries (anchor + dash)
            HyphenTwoPhaseComposer.Result ifRes = composer.composeEnglish(ifHit.template, ifHit.groups);
            writeDslrWhen(ifRes.dslrLines);
            emitDslEntries(ifRes.dslKeys, true, ConstraintCase.EXISTS); // IF uses EXISTS/neutral

            // THEN side: same split for DSLR, but DSL entries must encode violation semantics
            HyphenTwoPhaseComposer.Result thenRes = composer.composeEnglish(thenHit.template, thenHit.groups);
            writeDslrWhen(thenRes.dslrLines);
            emitDslEntries(thenRes.dslKeys, false, kase); // THEN: ALL_OF/AT_LEAST_ONE/NONE/EXACTLY_ONE

            // End rule
            dslr.endRule();
        }
    }

    private void writeDslrWhen(java.util.List<String> englishLines) throws IOException {
        if (englishLines.size() == 1) {
            dslr.whenFromEnglish(englishLines.get(0)); // method splits if it sees dash
            return;
        }
        // englishLines already carry "- " on second if dash; write both
        String anchor = englishLines.get(0);
        String dash = englishLines.get(1).replaceFirst("^\\s*-\\s*", "");
        dslr.whenFromEnglish(anchor);
        dslr.whenFromEnglish(" - " + dash);
    }

    private void emitDslEntries(java.util.List<String> englishKeys, boolean isIf, ConstraintCase kase) throws IOException {
        if (englishKeys.size() == 1) {
            dsl.emitEntries(englishKeys.get(0), isIf, kase);
            return;
        }
        // Two keys: anchor and "- dash"
        dsl.emitEntries(englishKeys.get(0), isIf, kase);
        dsl.emitEntries(englishKeys.get(1), isIf, kase);
    }

    @Override public void close() throws IOException { dsl.close(); dslr.close(); }
}
EOF

cat > "$SRC/Demo.java" <<'EOF'
import java.util.*;
public class Demo {
    public static void main(String[] args) throws Exception {
        List<RuleRow> rows = List.of(
            new RuleRow("BR-ALL-UNIVERSAL",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "all goodsitem.invoiceAmount.value is less than or equal to 135"),
            new RuleRow("BR-AT-LEAST-ONE",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "at least one GoodsItem additionalDocument type code equals C676"),
            new RuleRow("BR-NONE",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "none GoodsItem type code equals 999"),
            new RuleRow("BR-EXACTLY-ONE",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "exactly one GoodsItem additionalDocument type code equals C676")
        );

        String dslPath  = "dsl-dslr-hyphen-demo/build/rules.dsl";
        String dslrPath = "dsl-dslr-hyphen-demo/build/rules.dslr";

        try (WhenPatternMatcher w = new WhenPatternMatcher(dslPath, dslrPath)) {
            w.collectAll(rows);
        }

        System.out.println("Generated:");
        System.out.println("  DSL : " + dslPath);
        System.out.println("  DSLR: " + dslrPath);

        System.out.println("\n--- DSL ---");
        System.out.println(java.nio.file.Files.readString(java.nio.file.Path.of(dslPath)));
        System.out.println("--- DSLR ---");
        System.out.println(java.nio.file.Files.readString(java.nio.file.Path.of(dslrPath)));
    }
}
EOF

echo "Compiling..."
javac "$SRC"/*.java -d "$SRC"

echo "Running..."
java -cp "$SRC" Demo
