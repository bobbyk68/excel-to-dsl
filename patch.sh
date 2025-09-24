#!/usr/bin/env bash
set -euo pipefail

ROOT="dsl-and-dslr-demo"
SRC="$ROOT/src"
OUT="$ROOT/build"
mkdir -p "$SRC" "$OUT"

echo "Workspace: $ROOT"

########################################
# Models
########################################
cat > "$SRC/RuleRow.java" <<'EOF'
public class RuleRow {
    private final String id;
    private final String ifCondition;    // Excel IF (english)
    private final String thenCondition;  // Excel THEN (english)

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
    final String template;   // canonical english with {value}
    final String concrete;   // english with value substituted
    final String value;      // captured
    final boolean isIf;      // IF-side or THEN-side

    public AtomicHit(String template, String concrete, String value, boolean isIf) {
        this.template = template; this.concrete = concrete; this.value = value; this.isIf = isIf;
    }
}
EOF

########################################
# ConstraintCase with legacy aliases
########################################
cat > "$SRC/ConstraintCase.java" <<'EOF'
public enum ConstraintCase {
    ALL_OF, AT_LEAST_ONE, NONE, EXACTLY_ONE, EXISTS,
    ONE_OF, ANY_OF, NONE_OF;

    public boolean requiresFlip() { return canonical(this) == ALL_OF; }

    public static ConstraintCase canonical(ConstraintCase k) {
        return switch (k) {
            case ONE_OF -> EXACTLY_ONE;
            case ANY_OF -> AT_LEAST_ONE;
            case NONE_OF -> NONE;
            default -> k;
        };
    }

    public static ConstraintCase fromThenEnglish(String s) {
        if (s == null) return EXISTS;
        s = s.toLowerCase().trim();
        if (s.startsWith("all ")) return ALL_OF;
        if (s.startsWith("at least one") || s.startsWith("any ")) return AT_LEAST_ONE;
        if (s.startsWith("none")) return NONE;
        if (s.startsWith("exactly one") || s.startsWith("one of") || s.startsWith("exactly 1")) return EXACTLY_ONE;
        return EXISTS;
    }
}
EOF

########################################
# Writers
########################################
cat > "$SRC/DslWriter.java" <<'EOF'
import java.io.*;
import java.util.*;

public class DslWriter implements Closeable {
    private final File file;
    private final Set<String> emittedTemplates = new LinkedHashSet<>();
    private final BufferedWriter bw;

    public DslWriter(String path) throws IOException {
        this.file = new File(path);
        this.bw = new BufferedWriter(new FileWriter(file, false));
    }

    public void ensureEntry(String template, boolean isIf, ConstraintCase kase) throws IOException {
        // De-duplicate on the template text
        if (!emittedTemplates.add(template)) return;

        ConstraintCase canon = ConstraintCase.canonical(kase);

        String header = "[when] " + template + " =\n";
        String body;

        if (isIf || canon == ConstraintCase.EXISTS) {
            // IF: two-line anchor + refinement (no flip here)
            if (template.toLowerCase().contains("special procedure")) {
                body = "    $if : GoodsItemFacts( $seq : sequence )\n" +
                       "    - GoodsItemFacts( this == $if, specialProcedureCode != null, specialProcedureCode == \"{value}\" )\n";
            } else {
                // Fallback IF pattern (adjust for other IF templates you have)
                body = "    $if : GoodsItemFacts( $seq : sequence )\n" +
                       "    - GoodsItemFacts( this == $if, field != null, field == \"{value}\" )\n";
            }
        } else {
            // THEN: choose by case
            String tl = template.toLowerCase();
            if (canon == ConstraintCase.ALL_OF) {
                // Universal → violator with flipped operator in dashed line
                if (tl.contains("invoiceamount.value") || tl.contains("invoiceamount")) {
                    body = "    $then : GoodsItemFacts( $seq : sequence )\n" +
                           "    - GoodsItemFacts( this == $then,\n" +
                           "                      invoiceAmount != null,\n" +
                           "                      invoiceAmount.value != null,\n" +
                           "                      invoiceAmount.value.compareTo(new java.math.BigDecimal(\"{value}\")) > 0 )\n";
                } else {
                    // generic number field
                    body = "    $then : GoodsItemFacts( $seq : sequence )\n" +
                           "    - GoodsItemFacts( this == $then, numberField != null, numberField.compareTo(new java.math.BigDecimal(\"{value}\")) > 0 )\n";
                }
            } else if (canon == ConstraintCase.AT_LEAST_ONE || canon == ConstraintCase.NONE) {
                // Existential/prohibition → not(...)
                if (tl.contains("additionaldocument") || tl.contains("type code")) {
                    body = "    not( GoodsItemDocumentFact( typeCode != null, typeCode == \"{value}\" ) )\n";
                } else {
                    body = "    not( GoodsItemFacts( field != null, field == \"{value}\" ) )\n";
                }
            } else if (canon == ConstraintCase.EXACTLY_ONE) {
                // Cardinality → accumulate(... != 1)
                if (tl.contains("additionaldocument") || tl.contains("type code")) {
                    body = "    accumulate(\n" +
                           "      GoodsItemDocumentFact( typeCode != null, typeCode == \"{value}\" ),\n" +
                           "      $cnt : count(1)\n" +
                           "    ) and eval( $cnt != 1 )\n";
                } else {
                    body = "    accumulate(\n" +
                           "      GoodsItemFacts( field != null, field == \"{value}\" ),\n" +
                           "      $cnt : count(1)\n" +
                           "    ) and eval( $cnt != 1 )\n";
                }
            } else {
                body = "    // TODO mapping for: " + template + "\n";
            }
        }

        bw.write(header);
        bw.write(body);
        bw.write("\n");
    }

    @Override public void close() throws IOException { bw.flush(); bw.close(); }
    public String path() { return file.getAbsolutePath(); }
}
EOF

cat > "$SRC/DslrWriter.java" <<'EOF'
import java.io.*;

public class DslrWriter implements Closeable {
    private final BufferedWriter bw;
    private final String path;
    public DslrWriter(String path) throws IOException {
        this.path = path;
        this.bw = new BufferedWriter(new FileWriter(path, false));
    }

    public void beginRule(String ruleId) throws IOException {
        bw.write("rule \"" + ruleId + "\"\nwhen\n");
    }
    public void whenLine(String english) throws IOException {
        bw.write("  " + english + "\n");
    }
    public void endRule() throws IOException {
        bw.write("then\n  // TODO: RHS\nend\n\n");
    }

    @Override public void close() throws IOException { bw.flush(); bw.close(); }
    public String path() { return path; }
}
EOF

########################################
# A tiny matcher to build AtomicHits from English
# (Replace with your real regex + JSON patterns later)
########################################
cat > "$SRC/ToyMatcher.java" <<'EOF'
public class ToyMatcher {

    public AtomicHit matchIf(String english) {
        // Canonical IF template
        if (english.toLowerCase().contains("special procedure")) {
            String value = lastToken(english);
            String template = "at least one GoodsItem with special procedure code equals {value}";
            String concrete = template.replace("{value}", quoteIfNeeded(value));
            return new AtomicHit(template, concrete, stripQuotes(value), true);
        }
        // Fallback
        String value = lastToken(english);
        String template = "at least one Fact field equals {value}";
        return new AtomicHit(template, template.replace("{value}", value), value, true);
    }

    public AtomicHit matchThen(String english) {
        String s = english.toLowerCase().trim();
        if (s.startsWith("all ") && s.contains("less than or equal to")) {
            String value = lastToken(english);
            String template = "all goodsitem.invoiceAmount.value is less than or equal to {value}";
            return new AtomicHit(template, template.replace("{value}", stripQuotes(value)), stripQuotes(value), false);
        }
        if (s.startsWith("at least one")) {
            String value = lastToken(english);
            String template = "at least one GoodsItem additionalDocument type code equals {value}";
            return new AtomicHit(template, template.replace("{value}", stripQuotes(value)), stripQuotes(value), false);
        }
        if (s.startsWith("none")) {
            String value = lastToken(english);
            String template = "none GoodsItem type code equals {value}";
            return new AtomicHit(template, template.replace("{value}", stripQuotes(value)), stripQuotes(value), false);
        }
        if (s.startsWith("exactly one") || s.startsWith("exactly 1")) {
            String value = lastToken(english);
            String template = "exactly one GoodsItem additionalDocument type code equals {value}";
            return new AtomicHit(template, template.replace("{value}", stripQuotes(value)), stripQuotes(value), false);
        }
        // Fallback
        String value = lastToken(english);
        String template = "all goodsitem.someNumber is less than or equal to {value}";
        return new AtomicHit(template, template.replace("{value}", stripQuotes(value)), stripQuotes(value), false);
    }

    private static String lastToken(String s) {
        if (s == null || s.isBlank()) return "";
        String[] t = s.trim().split("\\s+");
        return t[t.length-1];
    }
    private static String stripQuotes(String v) { return v.replaceAll("^\"|\"$", ""); }
    private static String quoteIfNeeded(String v) {
        return v.matches("^[0-9.]+$") ? v : "\"" + stripQuotes(v) + "\"";
    }
}
EOF

########################################
# The generator (collectAll): emits BOTH DSL and DSLR
########################################
cat > "$SRC/Generator.java" <<'EOF'
import java.io.*;
import java.util.*;

public class Generator {

    private final ToyMatcher matcher = new ToyMatcher();

    public void run(List<RuleRow> rows, String dslPath, String dslrPath) throws IOException {
        try (DslWriter dsl = new DslWriter(dslPath);
             DslrWriter dslr = new DslrWriter(dslrPath)) {

            for (RuleRow row : rows) {
                // PASS 1: match
                AtomicHit ifHit   = matcher.matchIf(row.ifCondition());
                AtomicHit thenHit = matcher.matchThen(row.thenCondition());

                // PASS 2A: DSLR (English)
                dslr.beginRule(row.id());
                dslr.whenLine(ifHit.concrete);
                dslr.whenLine(thenHit.concrete);
                dslr.endRule();

                // PASS 2B: DSL (dictionary)
                dsl.ensureEntry(ifHit.template,  true,  ConstraintCase.EXISTS);
                ConstraintCase kase = ConstraintCase.fromThenEnglish(thenHit.template);
                dsl.ensureEntry(thenHit.template, false, kase);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        List<RuleRow> rows = List.of(
            new RuleRow("BR675_1479",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "all goodsitem.invoiceAmount.value is less than or equal to 135"),

            new RuleRow("BR_DOC_AT_LEAST_ONE",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "at least one GoodsItem additionalDocument type code equals C676"),

            new RuleRow("BR_NONE",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "none GoodsItem type code equals 999"),

            new RuleRow("BR_EXACTLY_ONE",
                "at least one GoodsItem with special procedure code equals \"C07\"",
                "exactly one GoodsItem additionalDocument type code equals C676")
        );

        String outDir = new java.io.File("dsl-and-dslr-demo/build").getAbsolutePath();
        String dsl  = outDir + "/rules.dsl";
        String dslr = outDir + "/rules.dslr";
        new Generator().run(rows, dsl, dslr);
        System.out.println("Generated:");
        System.out.println("  DSL : " + dsl);
        System.out.println("  DSLR: " + dslr);
    }
}
EOF

########################################
# Build & Run
########################################
echo "Compiling..."
javac "$SRC"/*.java -d "$SRC"

echo "Running..."
java -cp "$SRC" Generator

echo
echo "---- rules.dsl ----"
cat "$OUT/rules.dsl" || true
echo
echo "---- rules.dslr ----"
cat "$OUT/rules.dslr" || true
