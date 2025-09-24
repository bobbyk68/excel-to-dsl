#!/usr/bin/env bash
set -euo pipefail

ROOT="collectall-demo"
SRC="$ROOT/src"
mkdir -p "$SRC"
echo "Workspace: $ROOT"

########################################
# RuleRow.java (matches your model)
########################################
cat > "$SRC/RuleRow.java" <<'EOF'
public class RuleRow {
    private final String id;
    private final String ifCondition;    // Excel IF (english)
    private final String thenCondition;  // Excel THEN (english)

    public RuleRow(String id, String ifCondition, String thenCondition) {
        this.id = id;
        this.ifCondition = ifCondition;
        this.thenCondition = thenCondition;
    }
    public String id() { return id; }
    public String ifCondition() { return ifCondition; }
    public String thenCondition() { return thenCondition; }
}
EOF

########################################
# AtomicHit.java (pass-1 unit)
########################################
cat > "$SRC/AtomicHit.java" <<'EOF'
import java.util.List;

public class AtomicHit {
    private final String id;          // pattern id (mocked here)
    private final String dsl;         // concrete DSL (template with {value} already substituted)
    private final java.util.List<String> groups; // regex groups; [0] = value

    public AtomicHit(String id, String dsl, java.util.List<String> groups) {
        this.id = id;
        this.dsl = dsl;
        this.groups = groups;
    }
    public String id() { return id; }
    public String dsl() { return dsl; }
    public java.util.List<String> groups() { return groups; }
}
EOF

########################################
# RowHits.java (pair IF+THEN for pass-2)
########################################
cat > "$SRC/RowHits.java" <<'EOF'
import java.util.List;

public class RowHits {
    private final String businessRuleId;
    private final String operatorToken;   // derived from THEN english
    private final java.util.List<AtomicHit> hits; // index 0=IF, index 1=THEN
    private final RuleRow row;

    public RowHits(String businessRuleId, String operatorToken, java.util.List<AtomicHit> hits, RuleRow row) {
        this.businessRuleId = businessRuleId;
        this.operatorToken = operatorToken;
        this.hits = hits;
        this.row = row;
    }
    public String businessRuleId() { return businessRuleId; }
    public String operatorToken() { return operatorToken; }
    public java.util.List<AtomicHit> hits() { return hits; }
    public RuleRow row() { return row; }
}
EOF

########################################
# ConstraintCase.java (THEN semantics)
########################################
cat > "$SRC/ConstraintCase.java" <<'EOF'
public enum ConstraintCase {
    ALL_OF,         // "all ... must ..."
    AT_LEAST_ONE,   // "at least one ... must ..."
    NONE,           // "none ... must ..."
    EXACTLY_ONE,    // "exactly one ... must ..."
    EXISTS;         // IF side

    public boolean requiresFlip() { return this == ALL_OF; }

    public java.util.List<String> applyWrapper(String boundLine) {
        return switch (this) {
            case NONE, AT_LEAST_ONE -> java.util.List.of("not( " + boundLine + " )");
            case EXACTLY_ONE -> java.util.List.of(
                "accumulate(",
                "  " + boundLine + ",",
                "  $cnt : count(1)",
                ") and eval( $cnt != 1 )"
            );
            case ALL_OF, EXISTS -> java.util.List.of(boundLine);
        };
    }

    public static ConstraintCase fromThenEnglish(String thenEnglish) {
        String s = thenEnglish == null ? "" : thenEnglish.toLowerCase();
        if (s.startsWith("all ")) return ALL_OF;
        if (s.startsWith("at least one")) return AT_LEAST_ONE;
        if (s.startsWith("none")) return NONE;
        if (s.startsWith("exactly one") || s.startsWith("exactly 1")) return EXACTLY_ONE;
        return EXISTS;
    }
}
EOF

########################################
# TwoPhaseComposer.java (composeLeaf with reverse flag)
########################################
cat > "$SRC/TwoPhaseComposer.java" <<'EOF'
import java.util.ArrayList;
import java.util.List;

public class TwoPhaseComposer {

    public List<String> composeLeaf(ConstraintCase kase, String template, String value, boolean reverse) {
        if (template == null) template = "";
        String[] parts = template.split("\\s+-\\s+", 2);

        if (parts.length == 1) {
            // single line; wrapper decides (NONE/AT_LEAST_ONE/EXACTLY_ONE) or passes as-is
            String bound = bind(parts[0].trim(), value);
            return kase.applyWrapper(bound);
        }

        // two-line: left = anchor/existence, right = predicate
        String left  = parts[0].trim();
        String right = parts[1].trim();

        String existence = bind(left, value);

        String pred = reverse ? flipOperatorIn(right) : right;
        String predicate = "- " + bind(pred, value);

        List<String> out = new ArrayList<>(2);
        out.add(existence);
        out.add(predicate);
        return out;
    }

    private String bind(String template, String value) {
        String v = value == null ? "" : value.trim().replaceAll("^\"|\"$", "");
        return template.replace("{value}", v);
    }

    // Minimal operator flip for symbol forms
    private String flipOperatorIn(String s) {
        String t = " " + s + " ";
        t = t.replace(" <= ", " > ");
        t = t.replace(" < ",  " >= ");
        t = t.replace(" >= ", " < ");
        t = t.replace(" > ",  " <= ");
        t = t.replace(" == ", " != ");
        t = t.replace(" != ", " == ");
        return t.trim();
    }
}
EOF

########################################
# WhenPatternMatcher.java (collectAll: Pass 1 & Pass 2)
########################################
cat > "$SRC/WhenPatternMatcher.java" <<'EOF'
import java.util.*;

public class WhenPatternMatcher {

    private final TwoPhaseComposer composer = new TwoPhaseComposer();

    /**
     * collectAll: Pass 1 (match) + Pass 2 (compose)
     * Returns the full list of "when" lines for all rows.
     */
    public List<String> collectAll(List<RuleRow> rows) {
        // ---------- PASS 1: build RowHits (mocking pattern→DSL mapping for demo) ----------
        List<RowHits> staged = new ArrayList<>();
        for (RuleRow row : rows) {
            AtomicHit ifHit = toIfAtomic(row.ifCondition());
            AtomicHit thenHit = toThenAtomic(row.thenCondition());
            String token = deriveOperatorToken(row.thenCondition()); // from THEN english
            staged.add(new RowHits(row.id(), token, List.of(ifHit, thenHit), row));
        }

        // ---------- PASS 2: compose leaves (IF as EXISTS, THEN as kase) ----------
        List<String> out = new ArrayList<>();
        for (RowHits rh : staged) {
            ConstraintCase kase = ConstraintCase.fromThenEnglish(rh.row().thenCondition()); // derived from THEN
            out.add("// --- Rule " + rh.businessRuleId() + " (THEN=" + kase + ") ---");

            for (int i = 0; i < rh.hits().size(); i++) {
                AtomicHit hit = rh.hits().get(i);
                boolean isIf = (i == 0);
                ConstraintCase eff = isIf ? ConstraintCase.EXISTS : kase;
                boolean reverse = (!isIf) && eff.requiresFlip();

                String value = firstGroupOrEmpty(hit);
                List<String> lines = composer.composeLeaf(eff, hit.dsl(), value, reverse);
                out.addAll(lines);
            }
        }
        return out;
    }

    private String firstGroupOrEmpty(AtomicHit h) {
        return (h.groups() != null && !h.groups().isEmpty()) ? h.groups().get(0) : "";
    }

    private String deriveOperatorToken(String thenEnglish) {
        // Minimal mapping; you probably already have this elsewhere
        ConstraintCase c = ConstraintCase.fromThenEnglish(thenEnglish);
        return c.name();
    }

    // ===== Mock pattern→DSL mapping to keep demo self-contained =====

    private AtomicHit toIfAtomic(String ifEnglish) {
        // Example: "at least one goods item with special procedure equals 060"
        // Render as your two-line anchor+dashed (no flip)
        String dsl = "$if : GoodsItemFacts( $seq : sequence ) - GoodsItemFacts( this == $if, specialProcedureCode != null, specialProcedureCode == \"{value}\" )";
        String val = extractValue(ifEnglish);
        return new AtomicHit("IF_EXISTS_SP_EQ", dsl, java.util.List.of(val));
    }

    private AtomicHit toThenAtomic(String thenEnglish) {
        String s = thenEnglish.toLowerCase().trim();
        if (s.startsWith("all ") && s.contains("less than or equal to")) {
            // two-line universal; symbol form so flip works
            String dsl = "$then : GoodsItemFacts( $seq : sequence ) - GoodsItemFacts( this == $then, invoiceAmount != null, invoiceAmount.value != null, invoiceAmount.value.compareTo(new java.math.BigDecimal(\"{value}\")) <= 0 )";
            return new AtomicHit("THEN_ALL_OF_LTE", dsl, java.util.List.of(extractValue(thenEnglish)));
        }
        if (s.startsWith("at least one")) {
            // existential requirement → wrapper not(...)
            String dsl = "GoodsItemDocumentFact( typeCode != null, typeCode == \"{value}\" )";
            return new AtomicHit("THEN_AT_LEAST_ONE_EQ", dsl, java.util.List.of(extractValue(thenEnglish)));
        }
        if (s.startsWith("none")) {
            String dsl = "GoodsItemFacts( typeCode != null, typeCode == \"{value}\" )";
            return new AtomicHit("THEN_NONE_EQ", dsl, java.util.List.of(extractValue(thenEnglish)));
        }
        if (s.startsWith("exactly one")) {
            String dsl = "GoodsItemDocumentFact( typeCode != null, typeCode == \"{value}\" )";
            return new AtomicHit("THEN_EXACTLY_ONE_EQ", dsl, java.util.List.of(extractValue(thenEnglish)));
        }
        // fallback
        String dsl = "GoodsItemFacts( field == \"{value}\" )";
        return new AtomicHit("THEN_FALLBACK", dsl, java.util.List.of(extractValue(thenEnglish)));
    }

    private String extractValue(String english) {
        // ultra simple: last token; replace with your regex capture (.+)
        if (english == null) return "";
        String[] t = english.trim().split("\\s+");
        return t.length == 0 ? "" : t[t.length - 1].replaceAll("^\"|\"$", "");
    }
}
EOF

########################################
# Demo.java (runs collectAll with 4 rows)
########################################
cat > "$SRC/Demo.java" <<'EOF'
import java.util.*;

public class Demo {
    public static void main(String[] args) {
        List<RuleRow> rows = List.of(
            new RuleRow("BR-ALL-UNIVERSAL",
                "at least one goods item with special procedure equals 060",
                "all goodsitem.invoiceAmount.value is less than or equal to 135"),
            new RuleRow("BR-AT-LEAST-ONE",
                "at least one goods item with special procedure equals 060",
                "at least one GoodsItem additionalDocument typeCode equals C676"),
            new RuleRow("BR-NONE",
                "at least one goods item with special procedure equals 060",
                "none GoodsItem type code equals 999"),
            new RuleRow("BR-EXACTLY-ONE",
                "at least one goods item with special procedure equals 060",
                "exactly one GoodsItem with type code equals C676")
        );

        WhenPatternMatcher matcher = new WhenPatternMatcher();
        List<String> whenLines = matcher.collectAll(rows);

        System.out.println("=== GENERATED WHEN BLOCKS ===");
        for (String line : whenLines) {
            System.out.println(line);
        }
    }
}
EOF

echo "Compiling..."
(
  cd "$SRC"
  javac *.java
)

echo "Running..."
(
  cd "$SRC"
  java Demo
)
