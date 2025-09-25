#!/usr/bin/env bash
set -euo pipefail

# ======= CONFIG: change if your Java source root is different =======
SRC_DIR="src"

# Files to write (backups saved as *.bak if present)
FILES=(
  "HyphenTwoPhaseComposer.java"
  "DslrWriter.java"
  "DslWriter.java"
  "TypeRegistry.java"
  "WhenPatternMatcher.java"
)

mkdir -p "$SRC_DIR"

backup() { local f="$1"; [[ -f "$SRC_DIR/$f" ]] && cp "$SRC_DIR/$f" "$SRC_DIR/$f.bak" || true; }

for f in "${FILES[@]}"; do backup "$f"; done

# ===================== HyphenTwoPhaseComposer.java =====================
cat > "$SRC_DIR/HyphenTwoPhaseComposer.java" <<'JAVA'
// package your.package.here;

import java.util.*;

/**
 * Composes the English (DSLR lines + DSL keys) for IF/THEN clauses.
 * - Splits on " - " (anchor + dash).
 * - For THEN + ALL_OF, flips the DASH English to violator wording (<= -> >, == -> !=, etc.).
 * - For THEN + AT_LEAST_ONE, rewrites the ANCHOR to a negated existence English ("No matching ... exists")
 *   and keeps the dash as-is (no operator flip). The DSL writer will produce a fragmented NOT across
 *   two [when] entries that join into a single not(...) DRL pattern.
 *
 * NOTE: This class is English-only; it does NOT build Drools code. The Drools RHS is handled in DslWriter.
 */
public class HyphenTwoPhaseComposer {

    /** Role of the side being composed. */
    public enum Role { IF, THEN }

    /** Pair of English lines for DSLR + exact DSL keys to emit. */
    public static final class Result {
        public final List<String> dslrLines; // English for DSLR (1 or 2 physical lines; second starts with "- ")
        public final List<String> dslKeys;   // English keys for DSL (exactly the same text as DSLR lines)
        public Result(List<String> dslrLines, List<String> dslKeys) {
            this.dslrLines = dslrLines;
            this.dslKeys = dslKeys;
        }
    }

    /**
     * Compose English for a leaf:
     * @param role   IF or THEN
     * @param kase   Quantifier case (EXISTS, ALL_OF, AT_LEAST_ONE, NONE, EXACTLY_ONE)
     * @param englishTemplateWithPlaceholders canonical template from JSON; may contain " - " and {1},{2},...
     * @param groups captured values to bind for DSLR readability
     * @param excelThenAnchorIfAny normalized Excel THEN anchor (optional; used only if you want to override anchor text)
     */
    public Result composeEnglish(Role role,
                                 ConstraintCase kase,
                                 String englishTemplateWithPlaceholders,
                                 List<String> groups,
                                 String excelThenAnchorIfAny) {
        String bound = bind(englishTemplateWithPlaceholders, groups);
        String[] parts = bound.split("\\s+-\\s+", 2);

        // Single-line template
        if (parts.length == 1) {
            String line = parts[0].trim();
            return new Result(List.of(line), List.of(line));
        }

        // Two-part template
        String anchor = parts[0].trim(); // e.g., "Matching goods item with additional information"
        String dash   = parts[1].trim(); // e.g., "with code equals 123"

        // IF: never flip, never negate.
        if (role == Role.IF) {
            return new Result(List.of(anchor, "- " + dash), List.of(anchor, "- " + dash));
        }

        // THEN side: choose behaviour by case
        ConstraintCase canon = ConstraintCase.canonical(kase);

        // 1) Universal: flip the dash phrase to violator English
        if (canon == ConstraintCase.ALL_OF) {
            dash = flipDashEnglishToViolator(dash);
            return new Result(List.of(anchor, "- " + dash), List.of(anchor, "- " + dash));
        }

        // 2) Existential (AT_LEAST_ONE): phrase as "No matching ... exists" + original dash
        if (canon == ConstraintCase.AT_LEAST_ONE) {
            // Prefer deriving the negated anchor from the LEFT side of the JSON template.
            // If you want to force the Excel THEN anchor verbatim, pass it in excelThenAnchorIfAny and tweak below.
            String neg = negateExistenceEnglish(anchor); // "Matching X" -> "No matching X exists"
            // If caller wants the Excel wording specifically, uncomment:
            // if (excelThenAnchorIfAny != null && !excelThenAnchorIfAny.isBlank()) neg = excelThenAnchorIfAny;

            return new Result(List.of(neg, "- " + dash), List.of(neg, "- " + dash));
        }

        // 3) NONE / EXACTLY_ONE: English usually single-line; if your JSON is dashed, pass-through
        return new Result(List.of(anchor, "- " + dash), List.of(anchor, "- " + dash));
    }

    // ----- helpers: English only -----

    private String bind(String template, List<String> groups) {
        String out = template;
        for (int i = 0; i < groups.size(); i++) {
            out = out.replace("{"+(i+1)+"}", groups.get(i));
        }
        return out;
    }

    /** Flip operator words for THEN/ALL_OF dash English (violation phrasing). */
    private String flipDashEnglishToViolator(String dash) {
        String s = " " + dash.toLowerCase(Locale.ROOT).trim() + " ";

        // protect with placeholders (multi-word first)
        s = s.replace(" less than or equal to ", " __FLIP_GT__ ");
        s = s.replace(" greater than or equal to ", " __FLIP_LT__ ");
        s = s.replace(" not equal to ", " __FLIP_EQ__ ");
        s = s.replace(" equal to ", " __FLIP_NEQ__ ");
        s = s.replace(" equals ", " __FLIP_NEQ__ ");
        s = s.replace(" less than ", " __FLIP_GTE__ ");
        s = s.replace(" greater than ", " __FLIP_LTE__ ");

        // map back to flipped counterparts
        s = s.replace(" __FLIP_GT__ ", " greater than ");
        s = s.replace(" __FLIP_LT__ ", " less than ");
        s = s.replace(" __FLIP_GTE__ ", " greater than or equal to ");
        s = s.replace(" __FLIP_LTE__ ", " less than or equal to ");
        s = s.replace(" __FLIP_EQ__ ", " equal to ");
        s = s.replace(" __FLIP_NEQ__ ", " not equal to ");

        return s.trim();
    }

    /**
     * Negate an existence-style anchor text.
     * Examples:
     *  - "Matching goods item with additional information" -> "No matching goods item with additional information exists"
     *  - "Matching goods item" -> "No matching goods item exists"
     */
    private String negateExistenceEnglish(String positiveAnchor) {
        String a = positiveAnchor.trim();

        // Normalise initial "Matching"/"matching"
        if (a.startsWith("Matching ")) {
            a = "matching " + a.substring("Matching ".length());
        }

        // If it already ends with "exists", avoid duplication
        if (a.toLowerCase(Locale.ROOT).endsWith(" exists")) {
            return "No " + a;
        }
        return "No " + a + " exists";
    }
}
JAVA

# ========================= DslrWriter.java =========================
cat > "$SRC_DIR/DslrWriter.java" <<'JAVA'
// package your.package.here;

import java.io.*;

/**
 * Writes the DSLR "when" section with explicit anchor/dash methods
 * to avoid blank lines between them.
 */
public class DslrWriter implements Closeable {
    private final BufferedWriter bw;
    private final String path;

    public DslrWriter(String path) throws IOException {
        this.path = path;
        this.bw = new BufferedWriter(new FileWriter(path, false));
    }

    public void beginRule(String id) throws IOException {
        bw.write("rule \"" + id + "\"\nwhen\n");
    }

    /** Writes the anchor English line (2-space indent). */
    public void whenAnchor(String anchor) throws IOException {
        bw.write("  " + anchor + "\n");
    }

    /** Writes the dash English line (4-space indent, prefixed with '- '). */
    public void whenDash(String dash) throws IOException {
        bw.write("    - " + dash + "\n");
    }

    public void endRule() throws IOException {
        bw.write("then\n  // TODO RHS\nend\n\n");
    }

    @Override public void close() throws IOException { bw.flush(); bw.close(); }
    public String path() { return path; }
}
JAVA

# =========================== TypeRegistry.java ===========================
cat > "$SRC_DIR/TypeRegistry.java" <<'JAVA'
// package your.package.here;

import java.util.*;

/**
 * Minimal registry used by DslWriter to render Drools RHS from a typeKey.
 * In your real project, load these mappings from JSON/YAML.
 */
public final class TypeRegistry {

    /** Carries everything needed to emit the RHS constraints safely. */
    public static final class FieldMapping {
        public final String factType;            // e.g., GoodsItemAdditionalInformationFact
        public final String fieldPath;           // e.g., code, invoiceAmount.value
        public final String valueType;           // "String" | "BigDecimal" | ...
        public final List<String> nullGuards;    // e.g., ["invoiceAmount != null","invoiceAmount.value != null"]
        public final boolean sameItemJoin;       // joins to $if/$then via "this == $x"

        public FieldMapping(String factType, String fieldPath, String valueType,
                            List<String> nullGuards, boolean sameItemJoin) {
            this.factType = factType;
            this.fieldPath = fieldPath;
            this.valueType = valueType;
            this.nullGuards = nullGuards;
            this.sameItemJoin = sameItemJoin;
        }
    }

    // ---- SAMPLE DATA ----
    private final Map<String, FieldMapping> map = new HashMap<>() {{
        // Anchor-only binding to a GoodsItem (no field path, just a binder)
        put("GI_ANCHOR", new FieldMapping(
            "GoodsItemFacts", "", "", List.of(), true));

        // Special procedure (IF dash)
        put("GI_SP_CODE", new FieldMapping(
            "GoodsItemFacts", "specialProcedureCode", "String",
            List.of("specialProcedureCode != null"), true));

        // Additional information code (THEN AT_LEAST_ONE example)
        put("GI_ADDINFO_CODE", new FieldMapping(
            "GoodsItemAdditionalInformationFact", "code", "String",
            List.of("code != null"), true));

        // Invoice amount value (THEN ALL_OF numeric)
        put("GI_INVOICE_VALUE", new FieldMapping(
            "GoodsItemFacts", "invoiceAmount.value", "BigDecimal",
            List.of("invoiceAmount != null", "invoiceAmount.value != null"), true));
    }};

    public FieldMapping resolve(String key) {
        FieldMapping fm = map.get(key);
        if (fm == null) throw new IllegalArgumentException("Unknown type key: " + key);
        return fm;
    }
}
JAVA

# ============================ DslWriter.java =============================
cat > "$SRC_DIR/DslWriter.java" <<'JAVA'
// package your.package.here;

import java.io.*;
import java.util.*;

/**
 * Emits the DSL dictionary. This version understands:
 *  - IF (anchor + dash): two entries (binder + refinement)
 *  - THEN/ALL_OF: two entries (binder + VIOLATOR refinement)
 *  - THEN/AT_LEAST_ONE: two entries that JOIN into a single not(...) DRL pattern
 *    (anchor emits 'not(Fact(' prefix; dash emits guards + compare + '))').
 *  - Single-line cases (NONE / EXACTLY_ONE): one entry using not(...) or accumulate(...!=1).
 *
 * NOTE: This class expects type keys so it can ask TypeRegistry for fact/field/guards/valueType.
 */
public class DslWriter implements Closeable {
    private final BufferedWriter bw;
    private final String path;
    private final Set<String> emitted = new LinkedHashSet<>();

    public DslWriter(String path) throws IOException {
        this.path = path;
        this.bw = new BufferedWriter(new FileWriter(path, false));
    }
    public String path() { return path; }

    /**
     * Emit DSL entries for a (possibly) dashed template using registry-driven RHS.
     * @param englishKeys list of 1 or 2 keys; when 2, keys[0]=anchor, keys[1]="- dash"
     * @param isIf        true for IF side
     * @param kase        quantifier case for THEN (EXISTS for IF)
     * @param anchorTypeKey registry key for anchor (binder) fact
     * @param dashTypeKey   registry key for dash (field predicate) fact/field
     * @param reg        type registry
     */
    public void emitEntriesWithRegistry(List<String> englishKeys, boolean isIf, ConstraintCase kase,
                                        String anchorTypeKey, String dashTypeKey, TypeRegistry reg) throws IOException {
        if (englishKeys.size() == 1) {
            // Single-line (NONE / EXACTLY_ONE etc.)
            String key = englishKeys.get(0).trim();
            if (emitted.add(key)) {
                bw.write("[when] " + key + " =\n");
                for (String line : singleLineDroolsFromRegistry(key, isIf, kase, dashTypeKey, reg)) {
                    bw.write("    " + line + "\n");
                }
                bw.write("\n");
            }
            return;
        }

        String anchorKey = englishKeys.get(0).trim();
        String dashKey   = englishKeys.get(1).trim(); // already starts with "- "

        if (isIf) {
            // IF: binder + positive refinement
            if (emitted.add(anchorKey)) {
                bw.write("[when] " + anchorKey + " =\n");
                for (String line : renderBinder(anchorTypeKey, /*isIf*/ true, reg))
                    bw.write("    " + line + "\n");
            }
            if (emitted.add(dashKey)) {
                bw.write("[when] " + dashKey + " =\n");
                for (String line : renderDashPredicate(dashTypeKey, /*joinVar*/"$if", /*violator*/false, reg))
                    bw.write("    " + line + "\n");
                bw.write("\n");
            }
            return;
        }

        // THEN side
        ConstraintCase canon = ConstraintCase.canonical(kase);

        if (canon == ConstraintCase.ALL_OF) {
            // Universal: binder + VIOLATOR dash (operator flipped)
            if (emitted.add(anchorKey)) {
                bw.write("[when] " + anchorKey + " =\n");
                for (String line : renderBinder(anchorTypeKey, /*isIf*/ false, reg))
                    bw.write("    " + line + "\n");
            }
            if (emitted.add(dashKey)) {
                bw.write("[when] " + dashKey + " =\n");
                for (String line : renderDashPredicate(dashTypeKey, /*joinVar*/"$then", /*violator*/true, reg))
                    bw.write("    " + line + "\n");
                bw.write("\n");
            }
            return;
        }

        if (canon == ConstraintCase.AT_LEAST_ONE) {
            // Existential prohibition: two entries that JOIN into a single not(...) pattern.
            // 1) Anchor entry emits the NOT prefix and opens the Fact(
            if (emitted.add(anchorKey)) {
                bw.write("[when] " + anchorKey + " =\n");
                for (String line : renderNotPrefix(anchorTypeKey, /*joinVar*/"$then", reg))
                    bw.write("    " + line + "\n");
                // Intentionally NO blank line here; we "glue" dash right after
            }
            // 2) Dash entry emits field guards/predicate and closes )).
            if (emitted.add(dashKey)) {
                bw.write("[when] " + dashKey + " =\n");
                for (String line : renderNotSuffix(dashTypeKey, reg))
                    bw.write("    " + line + "\n");
                bw.write("\n"); // one blank line after the pair
            }
            return;
        }

        // Fallback: treat like IF-style (binder + refinement)
        if (emitted.add(anchorKey)) {
            bw.write("[when] " + anchorKey + " =\n");
            for (String line : renderBinder(anchorTypeKey, /*isIf*/ false, reg))
                bw.write("    " + line + "\n");
        }
        if (emitted.add(dashKey)) {
            bw.write("[when] " + dashKey + " =\n");
            for (String line : renderDashPredicate(dashTypeKey, /*joinVar*/"$then", /*violator*/false, reg))
                bw.write("    " + line + "\n");
            bw.write("\n");
        }
    }

    // ---------------- helpers: build RHS lines ----------------

    private List<String> renderBinder(String anchorTypeKey, boolean isIf, TypeRegistry reg) {
        TypeRegistry.FieldMapping fm = reg.resolve(anchorTypeKey);
        String var = isIf ? "$if" : "$then";
        return List.of(var + " : " + fm.factType + "( $seq : sequence )");
    }

    /** Positive/violator dash predicate bound to anchor var (this == $if/$then). */
    private List<String> renderDashPredicate(String dashTypeKey, String joinVar, boolean violator, TypeRegistry reg) {
        TypeRegistry.FieldMapping fm = reg.resolve(dashTypeKey);
        List<String> out = new ArrayList<>();
        StringBuilder first = new StringBuilder();
        first.append(fm.factType).append("( ");
        if (fm.sameItemJoin) first.append("this == ").append(joinVar).append(", ");
        if (!fm.nullGuards.isEmpty()) {
            first.append(fm.nullGuards.get(0)).append(",");
            out.add(first.toString());
            for (int i = 1; i < fm.nullGuards.size(); i++) {
                out.add("                " + fm.nullGuards.get(i) + ",");
            }
        } else {
            out.add(first.toString());
        }

        String comp;
        if ("BigDecimal".equals(fm.valueType)) {
            // <= becomes > for violator; otherwise <=/</>=/>/==/!= driven by your template—simplified here as <=/>
            comp = fm.fieldPath + ".compareTo(new java.math.BigDecimal(\"{1}\")) " + (violator ? ">" : "<=") + " 0";
        } else {
            comp = fm.fieldPath + (violator ? " != " : " == ") + "\"{1}\"";
        }
        out.add("                " + comp + " )");
        return out;
    }

    /** Emits: not( <Fact>( this == $then, [guards,]   (no trailing newline) */
    private List<String> renderNotPrefix(String anchorTypeKey, String joinVar, TypeRegistry reg) {
        TypeRegistry.FieldMapping fm = reg.resolve(anchorTypeKey);
        StringBuilder sb = new StringBuilder();
        sb.append("not( ").append(fm.factType).append("( ");
        if (fm.sameItemJoin) sb.append("this == ").append(joinVar).append(", ");
        // guards (if any) are appended in suffix; leave as opening only.
        return List.of(sb.toString());
    }

    /** Emits the remainder for not(...): guards + equality + ") )" */
    private List<String> renderNotSuffix(String dashTypeKey, TypeRegistry reg) {
        TypeRegistry.FieldMapping fm = reg.resolve(dashTypeKey);
        List<String> out = new ArrayList<>();
        // Guards first (each as its own line if multiple)
        for (String g : fm.nullGuards) {
            out.add(g + ",");
        }
        // Equality compare (string or numeric)
        String cmp = "String".equals(fm.valueType)
            ? fm.fieldPath + " == \"{1}\""
            : fm.fieldPath + ".compareTo(new java.math.BigDecimal(\"{1}\")) == 0";
        out.add(cmp + " ) )");
        // Note: closes Fact ) and not )
        // If you need indentation alignment, the caller prepends 4 spaces per line.
        return out;
    }

    private List<String> singleLineDroolsFromRegistry(String englishKey, boolean isIf, ConstraintCase kase,
                                                      String dashTypeKey, TypeRegistry reg) {
        ConstraintCase canon = ConstraintCase.canonical(kase);
        TypeRegistry.FieldMapping fm = reg.resolve(dashTypeKey);

        if (canon == ConstraintCase.AT_LEAST_ONE || canon == ConstraintCase.NONE) {
            // not( Fact( guards, field == "{1}" ) )
            String cmp = "String".equals(fm.valueType)
                ? fm.fieldPath + " == \"{1}\""
                : fm.fieldPath + ".compareTo(new java.math.BigDecimal(\"{1}\")) == 0";
            String guards = String.join(", ", fm.nullGuards);
            return List.of("not( " + fm.factType + "( " +
                (guards.isEmpty() ? "" : guards + ", ") + cmp + " ) )");
        }
        if (canon == ConstraintCase.EXACTLY_ONE) {
            String cmp = "String".equals(fm.valueType)
                ? fm.fieldPath + " == \"{1}\""
                : fm.fieldPath + ".compareTo(new java.math.BigDecimal(\"{1}\")) == 0";
            String guards = String.join(", ", fm.nullGuards);
            return List.of(
                "accumulate(",
                "  " + fm.factType + "( " + (guards.isEmpty() ? "" : guards + ", ") + cmp + " ),",
                "  $cnt : count(1)",
                ") and eval( $cnt != 1 )"
            );
        }
        // default: plain positive
        String cmp = "String".equals(fm.valueType)
            ? fm.fieldPath + " == \"{1}\""
            : fm.fieldPath + ".compareTo(new java.math.BigDecimal(\"{1}\")) == 0";
        String guards = String.join(", ", fm.nullGuards);
        return List.of(fm.factType + "( " + (guards.isEmpty() ? "" : guards + ", ") + cmp + " )");
    }

    @Override public void close() throws IOException { bw.flush(); bw.close(); }
}
JAVA

# ======================= WhenPatternMatcher.java =======================
cat > "$SRC_DIR/WhenPatternMatcher.java" <<'JAVA'
// package your.package.here;

import java.io.*;
import java.util.*;

/**
 * Orchestrates Pass-1 (pattern match) and Pass-2 (compose & emit).
 * This skeleton shows how to call the composer and the DSL/DSLR writers
 * for the two key cases:
 *  - THEN/ALL_OF (violator dash flip)
 *  - THEN/AT_LEAST_ONE (fragmented not across anchor + dash)
 *
 * Replace PatternLibrary.findMatch(...) with your real JSON-driven matcher that
 * also carries anchorTypeKey/dashTypeKey on the hit (not shown here).
 */
public class WhenPatternMatcher implements Closeable {
    private final DslWriter dsl;
    private final DslrWriter dslr;
    private final HyphenTwoPhaseComposer composer = new HyphenTwoPhaseComposer();
    private final TypeRegistry registry = new TypeRegistry();

    public WhenPatternMatcher(String dslPath, String dslrPath) throws IOException {
        this.dsl = new DslWriter(dslPath);
        this.dslr = new DslrWriter(dslrPath);
    }

    public void collectAll(List<RuleRow> rows) throws IOException {
        for (RuleRow row : rows) {
            // === Pass 1: match both sides (stubbed; plug your JSON matcher here) ===
            AtomicHit ifHit   = PatternLibrary.findMatch(row.ifCondition(), true);
            AtomicHit thenHit = PatternLibrary.findMatch(row.thenCondition(), false);

            // Derive THEN case ONLY from the Excel THEN anchor
            ConstraintCase kase = ConstraintCase.fromEnglish(row.thenCondition());

            // === DSLR ===
            dslr.beginRule(row.id());

            HyphenTwoPhaseComposer.Result ifRes = composer.composeEnglish(
                HyphenTwoPhaseComposer.Role.IF,
                ConstraintCase.EXISTS,
                ifHit.template,
                ifHit.groups,
                null // not needed for IF
            );
            writeDslrWhen(ifRes.dslrLines);

            HyphenTwoPhaseComposer.Result thenRes = composer.composeEnglish(
                HyphenTwoPhaseComposer.Role.THEN,
                kase,
                thenHit.template,
                thenHit.groups,
                row.thenCondition() // optional assist when you want Excel anchor verbatim
            );
            writeDslrWhen(thenRes.dslrLines);

            dslr.endRule();

            // === DSL ===
            // NOTE: replace the sample typeKeys with the real ones you carry on your AtomicHit
            String ifAnchorTypeKey   = "GI_ANCHOR";
            String ifDashTypeKey     = "GI_SP_CODE";
            String thenAnchorTypeKey = "GI_ANCHOR";        // binder for $then
            String thenDashTypeKey   = kase == ConstraintCase.ALL_OF ? "GI_INVOICE_VALUE" : "GI_ADDINFO_CODE";

            // IF side
            dsl.emitEntriesWithRegistry(ifRes.dslKeys, true, ConstraintCase.EXISTS,
                ifAnchorTypeKey, ifDashTypeKey, registry);

            // THEN side
            dsl.emitEntriesWithRegistry(thenRes.dslKeys, false, kase,
                thenAnchorTypeKey, thenDashTypeKey, registry);
        }
    }

    private void writeDslrWhen(List<String> englishLines) throws IOException {
        if (englishLines.size() == 1) {
            dslr.whenAnchor(englishLines.get(0));
            return;
        }
        String anchor = englishLines.get(0);
        String dash   = englishLines.get(1).replaceFirst("^\\s*-\\s*", "");
        dslr.whenAnchor(anchor);
        dslr.whenDash(dash);
    }

    @Override public void close() throws IOException { dsl.close(); dslr.close(); }
}
JAVA

echo "✅ Files written to $SRC_DIR:"
for f in "${FILES[@]}"; do echo " - $SRC_DIR/$f"; done
echo "Backups (if any): *.bak"
echo
echo "Next steps:"
echo "  - Wire your real JSON matcher in place of PatternLibrary and feed typeKeys per hit."
echo "  - For THEN/AT_LEAST_ONE the DSL now emits a fragmented not(...) across anchor+dash."
echo "  - For THEN/ALL_OF the dash English is flipped to violator wording in DSLR+DSL key."
