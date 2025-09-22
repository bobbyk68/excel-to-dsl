#!/usr/bin/env bash
set -euo pipefail

# Create a clean workspace
ROOT_DIR="rules-dslr-demo"
SRC_DIR="$ROOT_DIR/src"
mkdir -p "$SRC_DIR"
echo "Workspace: $ROOT_DIR"

########################################################################
# Write source files
########################################################################

cat > "$SRC_DIR/EnglishQuantifier.java" <<'EOF'
public enum EnglishQuantifier {
    EXISTS, FOR_ALL, NONE, EXACTLY, ATOMIC;

    public static EnglishQuantifier fromText(String english) {
        String s = (english == null ? "" : english).toLowerCase().trim();
        if (s.startsWith("there is at least one") || s.startsWith("at least one")) return EXISTS;
        if (s.startsWith("all ") || s.contains(" must all ") || s.contains(" must be all ")) return FOR_ALL;
        if (s.startsWith("none ") || s.contains(" must not ") || s.contains(" no ")) return NONE;
        if (s.startsWith("exactly")) return EXACTLY;
        return ATOMIC;
    }

    /** "exactly one ..." -> 1, "exactly 2 ..." -> 2 */
    public static int extractExactlyN(String english) {
        if (english == null) throw new IllegalArgumentException("english is null");
        String[] t = english.toLowerCase().split("\\s+");
        if (t.length > 1 && t[0].equals("exactly")) return Integer.parseInt(t[1]);
        throw new IllegalArgumentException("Not an 'exactly' phrase: " + english);
    }
}
EOF

cat > "$SRC_DIR/EnglishOperator.java" <<'EOF'
public enum EnglishOperator {
    LT("<"), LTE("<="), GT(">"), GTE(">="), EQ("=="), NE("!=");

    private final String symbol;
    EnglishOperator(String s){ this.symbol = s; }
    public String symbol(){ return symbol; }

    public static EnglishOperator fromText(String english) {
        String s = (english == null ? "" : english).toLowerCase();
        // normalise symbols/wording
        s = s.replace("≤","less than or equal to").replace("≥","greater than or equal to");
        if (s.contains("less than or equal to") || s.contains("less or equal to") || s.contains("<=")) return LTE;
        if (s.contains("greater than or equal to") || s.contains("greater or equal to") || s.contains(">=")) return GTE;
        if (s.matches(".*\\bless than\\b.*") || s.contains(" < ")) return LT;
        if (s.matches(".*\\bgreater than\\b.*") || s.contains(" > ")) return GT;
        if (s.matches(".*\\bnot equal\\b.*") || s.contains("!=")) return NE;
        if (s.matches(".*\\bequal to\\b.*") || s.contains(" equals ") || s.contains("==")) return EQ;
        // fallback: if no operator keywords, default to EQ (useful for "equals C676")
        return EQ;
    }

    /** flip for violator logic in FOR_ALL */
    public EnglishOperator flipped() {
        switch (this) {
            case LTE: return GT;
            case LT:  return GTE;
            case GTE: return LT;
            case GT:  return LTE;
            case EQ:  return NE;
            case NE:  return EQ;
            default:  throw new IllegalStateException("Unexpected op: " + this);
        }
    }
}
EOF

cat > "$SRC_DIR/ThenEmitter.java" <<'EOF'
public class ThenEmitter {

    // ===== PUBLIC BRANCHES =====

    /** IF/EXISTS anchor + bare refinement line (caller may append more constraints) */
    public static String[] existsTwoLine(String fact) {
        String anchor = "$match : " + fact + "( $seq : sequence )";
        String dashed = "- " + fact + "( this == $match )";
        return new String[]{ anchor, dashed };
    }

    /** THEN universal (“all … op V”) → 2 lines; dashed uses flipped operator (violator) */
    public static String[] forAllTwoLine(String fact, String fieldPath, EnglishOperator originalOp, String valueLiteral) {
        String anchor = "$match : " + fact + "( $seq : sequence )";
        String guards = nullGuards(fieldPath);
        String cmp    = bigDecCmp(fieldPath, originalOp.flipped(), valueLiteral);  // e.g., <= → >
        String dashed = "- " + fact + "( this == $match, " + guards + ", " + cmp + " )";
        return new String[]{ anchor, dashed };
    }

    /** THEN prohibition (“none … with P”) → single not(...) */
    public static String noneSingleLine(String fact, String innerConditionDsl) {
        return "not( " + fact + "( " + innerConditionDsl + " ) )";
    }

    /** THEN exactly N → single accumulate with “!= N” (reversed to fire on violation) */
    public static String exactlyNReversedSingleLine(String fact, String innerConditionDsl, int n) {
        return "accumulate(\n" +
               "  " + fact + "( " + innerConditionDsl + " ),\n" +
               "  $cnt : count(1)\n" +
               ") and eval( $cnt != " + n + " )";
    }

    // ===== INTERNAL HELPERS =====

    // a.b.c -> "a != null, a.b != null, a.b.c != null"
    static String nullGuards(String path) {
        String[] parts = path.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.join(".", java.util.Arrays.copyOfRange(parts, 0, i + 1))).append(" != null");
        }
        return sb.toString();
    }

    // BigDecimal compare mapping; no eval inside patterns
    static String bigDecCmp(String fieldPath, EnglishOperator op, String val) {
        String lhs = fieldPath + ".compareTo(new java.math.BigDecimal(\"" + val + "\"))";
        switch (op) {
            case LT:  return lhs + " < 0";
            case LTE: return lhs + " <= 0";
            case GT:  return lhs + " > 0";
            case GTE: return lhs + " >= 0";
            case EQ:  return lhs + " == 0";
            case NE:  return lhs + " != 0";
            default:  throw new IllegalArgumentException("Unsupported op: " + op);
        }
    }
}
EOF

cat > "$SRC_DIR/AtomicHit.java" <<'EOF'
/** Minimal DTO – adapt to your matcher’s actual output */
public class AtomicHit {
    private final String english;   // full English clause (e.g., "all ... less than or equal to 135")
    private final String fact;      // e.g., "GoodsItemFacts"
    private final String fieldPath; // e.g., "invoiceAmount.value" or "typeCode"
    private final String value;     // e.g., "135" or "C676"

    public AtomicHit(String english, String fact, String fieldPath, String value) {
        this.english = english;
        this.fact = fact;
        this.fieldPath = fieldPath;
        this.value = value;
    }

    public String english() { return english; }
    public String fact() { return fact; }
    public String fieldPath() { return fieldPath; }
    public String value() { return value; }
}
EOF

cat > "$SRC_DIR/CollectAll.java" <<'EOF'
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectAll {

    /** Main entry: route each English clause to the correct emission */
    public List<String> collectAll(List<AtomicHit> hits) {
        List<String> out = new ArrayList<>();
        for (AtomicHit h : hits) {
            EnglishQuantifier q = EnglishQuantifier.fromText(h.english());

            switch (q) {
                case EXISTS: {
                    // IF-side: two lines (anchor + dash). You can refine the dashed line later.
                    String[] lines = ThenEmitter.existsTwoLine(h.fact());
                    Collections.addAll(out, lines);
                    break;
                }
                case FOR_ALL: {
                    // THEN universal → two lines, dash uses reversed operator
                    EnglishOperator op = EnglishOperator.fromText(h.english());
                    String[] lines = ThenEmitter.forAllTwoLine(h.fact(), h.fieldPath(), op, h.value());
                    Collections.addAll(out, lines);
                    break;
                }
                case NONE: {
                    // THEN prohibition → single not(...)
                    String inner = buildInnerEquality(h.fieldPath(), h.value());
                    out.add(ThenEmitter.noneSingleLine(h.fact(), inner));
                    break;
                }
                case EXACTLY: {
                    // THEN exactly N → single accumulate with "!= N"
                    int n = EnglishQuantifier.extractExactlyN(h.english());
                    String inner = buildInnerEquality(h.fieldPath(), h.value());
                    out.add(ThenEmitter.exactlyNReversedSingleLine(h.fact(), inner, n));
                    break;
                }
                case ATOMIC: {
                    // Simple fallback single-line (rare)
                    String inner = buildInnerEquality(h.fieldPath(), h.value());
                    out.add(h.fact() + "( " + inner + " )");
                    break;
                }
                default:
                    throw new IllegalStateException("Unexpected quantifier: " + q);
            }
        }
        return out;
    }

    // ----- helpers: guards + equality (string or numeric) -----
    private String buildInnerEquality(String fieldPath, String value) {
        boolean numeric = value != null && value.matches("-?\\d+(\\.\\d+)?");
        String guards = nullGuards(fieldPath);
        String v = numeric ? value : "\"" + value + "\"";
        return guards + ", " + fieldPath + " == " + v;
    }

    private String nullGuards(String path) {
        return ThenEmitter.nullGuards(path);
    }
}
EOF

cat > "$SRC_DIR/Demo.java" <<'EOF'
import java.util.Arrays;
import java.util.List;

public class Demo {
    public static void main(String[] args) {
        CollectAll gen = new CollectAll();

        // 1) THEN universal: "all invoiceAmounts <= 135" -> two lines; dashed uses '>' (reversed)
        AtomicHit h1 = new AtomicHit(
            "all goodsitem.invoiceAmount.value is less than or equal to 135",
            "GoodsItemFacts",
            "invoiceAmount.value",
            "135"
        );

        // 2) THEN exactly one: "exactly one ... == C676" -> single accumulate with '!= 1'
        AtomicHit h2 = new AtomicHit(
            "exactly one GoodsItem with type code equals C676",
            "GoodsItemFacts",
            "typeCode",
            "C676"
        );

        // 3) THEN none: "none of the GoodsItem have type code 999" -> single not(...)
        AtomicHit h3 = new AtomicHit(
            "none of the GoodsItem have type code equals 999",
            "GoodsItemFacts",
            "typeCode",
            "999"
        );

        List<String> out = gen.collectAll(Arrays.asList(h1, h2, h3));

        System.out.println("=== GENERATED WHEN LINES ===");
        for (String line : out) {
            System.out.println(line);
        }

        // Expected highlights:
        // - For h1 you should see:
        //   $match : GoodsItemFacts( $seq : sequence )
        //   - GoodsItemFacts( this == $match, invoiceAmount != null, invoiceAmount.value != null,
        //                     invoiceAmount.value.compareTo(new java.math.BigDecimal("135")) > 0 )
        //
        // - For h2 you should see an accumulate with '!= 1'
        // - For h3 you should see: not( GoodsItemFacts( typeCode != null, typeCode == "999" ) )
    }
}
EOF

########################################################################
# Compile and run
########################################################################
echo "Compiling..."
(
  cd "$SRC_DIR"
  javac *.java
)

echo "Running demo..."
(
  cd "$SRC_DIR"
  java Demo
)

echo ""
echo "Done. Files in: $SRC_DIR"
echo "Tip: edit Demo.java to add more English clauses and re-run."
