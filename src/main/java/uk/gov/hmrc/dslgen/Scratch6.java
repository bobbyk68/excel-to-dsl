package uk.gov.hmrc.dslgen.emit.parse;

import java.util.*;
import java.util.regex.*;
import uk.gov.hmrc.dslgen.emit.adapter.AtomicHitAdapter;

/** Adapts your existing RuleRow (with ifCondition/thenCondition in English) into AtomicHits. */
public final class RuleRowAdapter {

    private RuleRowAdapter() {}

    // === Public API (uses your existing method names) ===
    public static AtomicHitAdapter.AtomicHit left(RuleRow row)  { return parsePair(row.ifCondition()).left; }
    public static AtomicHitAdapter.AtomicHit right(RuleRow row) { return parsePair(row.ifCondition()).right; }
    public static AtomicHitAdapter.AtomicHit then(RuleRow row)  { return parseThen(row.thenCondition()); }

    // ====== Implementation ======

    /** Minimal English → AtomicHit for the THEN column. */
    static AtomicHitAdapter.AtomicHit parseThen(String thenCondition) {
        if (thenCondition == null) return null;
        String s = thenCondition.toLowerCase(Locale.ROOT);
        String token;
        if (s.contains("requested and previous"))           token = "RP_PP";
        else if (s.contains("requested procedure and special procedure")) token = "REQ_SP";
        else if (s.contains("special procedure and additional information")) token = "SP_AI";
        else if (s.contains("special procedure and additional document"))   token = "SP_AD";
        else if (s.contains("special procedure"))           token = "SP";
        else token = "VALIDATION";

        return new Hit("", token, "", "");
    }

    /** Parse the IF English into (left, right) AtomicHits. */
    static Pair parsePair(String ifCondition) {
        if (ifCondition == null) return new Pair(new Hit("GI","UNKNOWN","=",""), new Hit("GI","UNKNOWN","=",""));

        // normalise lines
        String text = ifCondition.replace("\r","").trim();
        String[] lines = text.split("\n");

        Hit left  = null;
        Hit right = null;

        for (String line : lines) {
            String l = line.trim().toLowerCase(Locale.ROOT);

            // SP: "- with code equals/in/not in <value>"
            Matcher mSp = SP_LINE.matcher(l);
            if (mSp.find()) {
                String op = normOp(mSp.group(1));
                String val = mSp.group(2).trim();
                Hit hit = new Hit("SP", "SP_CODE", op, val);
                if (left == null) left = hit; else if (right == null) right = hit;
                continue;
            }

            // RP: "- with requested procedure code ..."
            Matcher mRp = RP_LINE.matcher(l);
            if (mRp.find()) {
                Hit hit = new Hit("GI", "REQ_PROC", normOp(mRp.group(1)), mRp.group(2).trim());
                if (left == null) left = hit; else if (right == null) right = hit;
                continue;
            }

            // PP: "- with previous procedure code ..."
            Matcher mPp = PP_LINE.matcher(l);
            if (mPp.find()) {
                Hit hit = new Hit("GI", "PREV_PROC", normOp(mPp.group(1)), mPp.group(2).trim());
                if (left == null) left = hit; else if (right == null) right = hit;
                continue;
            }

            // AI: "- with code ..." under an "additional information" block
            if (l.contains("additional information")) currentAnchor = "AI";
            if (l.contains("additional document"))    currentAnchor = "AD";

            Matcher mGeneric = GENERIC_CODE_LINE.matcher(l);
            if (mGeneric.find() && currentAnchor != null) {
                String field = currentAnchor.equals("AD") ? "AD_TYPE_CODE" : "AI_CODE";
                Hit hit = new Hit(currentAnchor, field, normOp(mGeneric.group(1)), mGeneric.group(2).trim());
                if (left == null) left = hit; else if (right == null) right = hit;
            }
        }

        if (left == null)  left  = new Hit("GI","UNKNOWN","=","");
        if (right == null) right = left;

        // reset for next call
        currentAnchor = null;
        return new Pair(left, right);
    }

    // ====== Regex & helpers ======
    private static final Pattern SP_LINE  = Pattern.compile("-\\s*with\\s*code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern RP_LINE  = Pattern.compile("-\\s*with\\s*requested\\s*procedure\\s*code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern PP_LINE  = Pattern.compile("-\\s*with\\s*previous\\s*procedure\\s*code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern GENERIC_CODE_LINE = Pattern.compile("-\\s*with\\s*(?:type\\s*)?code\\s*(equals|in|not in)\\s*(.+)");
    private static String currentAnchor = null;

    private static String normOp(String op) {
        String t = op == null ? "=" : op.trim().toLowerCase(Locale.ROOT);
        if ("in".equals(t)) return "in";
        if ("not in".equals(t)) return "not in";
        return "equals";
    }

    /** Minimal AtomicHit implementation. */
    private record Hit(String a, String f, String o, String v) implements AtomicHitAdapter.AtomicHit {
        public String getAnchorToken()   { return a; }
        public String getFieldToken()    { return f; }
        public String getOperatorToken() { return o; }
        public String getRawValue()      { return v; }
    }

    /** Pair tuple. */
    private static final class Pair {
        final Hit left, right;
        Pair(Hit l, Hit r) { this.left = l; this.right = r; }
    }

    // ===== Contract your existing class should satisfy (names kept) =====
    public interface RuleRow {
        String id();
        String ifCondition();    // existing English IF
        String thenCondition();  // existing English THEN
        String errorCode();
        java.util.List<String> declarationType();
        java.util.List<String> procedureCategory();
        String param();          // untouched
        // mergedThenCodes / mergedThenCodesCsv also kept, not used here
    }
}

package uk.gov.hmrc.dslgen.emit;

import java.util.List;
import java.util.StringJoiner;
import uk.gov.hmrc.dslgen.emit.adapter.AtomicHitAdapter;
import uk.gov.hmrc.dslgen.emit.parse.RuleRowAdapter;

public final class RuleCollector {

    private final EmitterRegistry registry;

    public RuleCollector(EmitterRegistry registry) {
        this.registry = registry;
    }

    /** Main entry: read RuleRow → emit DSLR body → wrap with rule header → hand to sink. */
    public void collectAll(List<RuleRowAdapter.RuleRow> rows,
                           java.util.function.BiConsumer<RuleRowAdapter.RuleRow,String> sink) {

        DslrFileWriter dsl = new DslrFileWriter();

        for (RuleRowAdapter.RuleRow row : rows) {
            // 1) Build AtomicHits from your existing strings (no renames)
            AtomicHitAdapter.AtomicHit leftHit  = RuleRowAdapter.left(row);
            AtomicHitAdapter.AtomicHit rightHit = RuleRowAdapter.right(row);
            AtomicHitAdapter.AtomicHit thenHit  = RuleRowAdapter.then(row);

            // 2) Build EmitContext with derived anchor + then-target
            EmitContext ctx = EmitContextFactory.fromHits(leftHit, rightHit, thenHit);

            // 3) Emit body
            dsl.reset();
            boolean handled = registry.dispatch(ctx, dsl);
            String body = handled ? dsl.getText() : "[no emitter handled]";

            // 4) Wrap with your rule “envelope” (id, annotations from RuleRow)
            String fullRule = wrapWithHeader(row, body);

            // 5) Hand to the caller to write to file/db/etc.
            sink.accept(row, fullRule);
        }
    }

    // === Helper: builds your header exactly once per rule ===
    private String wrapWithHeader(RuleRowAdapter.RuleRow row, String body) {
        StringJoiner dt = new StringJoiner(",", "@declarationType(\"", "\")");
        for (String d : row.declarationType()) dt.add(d);

        StringJoiner pc = new StringJoiner(",", "@procedureCategory(\"", "\")");
        for (String p : row.procedureCategory()) pc.add(p);

        StringBuilder sb = new StringBuilder();
        sb.append("rule \"").append(row.id()).append("\"\n");
        sb.append("@ErrorCode(\"").append(row.errorCode()).append("\")\n");
        if (!row.declarationType().isEmpty()) sb.append(dt).append("\n");
        if (!row.procedureCategory().isEmpty()) sb.append(pc).append("\n");
        sb.append(body);
        sb.append("end\n");
        return sb.toString();
    }
}

new RuleCollector(registry).collectAll(ruleRows, (row, text) -> writeToWherever(row.id(), text));

        package uk.gov.hmrc.dslgen.emit;

import java.util.List;

public class DefaultFallbackEmitter implements Emitter {

    // Lowest priority so every specific emitter wins first.
    @Override public int priority() { return 0; }

    // Always willing to handle if we got at least one condition.
    @Override public boolean canHandle(EmitContext ctx) {
        return ctx != null && ctx.conditions() != null && !ctx.conditions().isEmpty();
    }

    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        List<Condition> conds = ctx.conditions();

        // 1) First condition → primary block
        Condition c1 = conds.get(0);
        dsl.whenLine(primaryExistsText(c1.anchorKey()));
        dsl.whenLine("- with " + fieldLabel(c1.fieldKey()) + " " + opWord(c1.operator()) + " " + quote(c1.displayValue()));

        // 2) Second condition (if present) → matching block
        if (conds.size() > 1) {
            Condition c2 = conds.get(1);
            dsl.whenLine(matchingExistsText(c2.anchorKey()));
            dsl.whenLine("- with " + fieldLabel(c2.fieldKey()) + " " + opWord(c2.operator()) + " " + quote(c2.displayValue()));
        }

        // 3) THEN — best effort using ThenPart semantic key, else generic
        String target = (ctx.thenPart() != null && ctx.thenPart().targetKey() != null)
                ? ctx.thenPart().targetKey()
                : "validation";
        dsl.thenLine(thenLineFor(target));
    }

    /* -------- wording helpers (hard-coded but generic) -------- */

    private String primaryExistsText(String anchor) {
        if (anchor != null && anchor.startsWith("GoodsItem.specialProcedures")) return "Goods item with special procedure exists";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalInformation")) return "Goods item with additional information exists";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalDocuments")) return "Goods item with additional document exists";
        return "Goods item exists";
    }

    private String matchingExistsText(String anchor) {
        if (anchor != null && anchor.startsWith("GoodsItem.specialProcedures")) return "Matching goods item with special procedure exists";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalInformation")) return "Matching goods item with additional information exists";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalDocuments")) return "Matching goods item with additional document exists";
        return "Matching goods item exists";
    }

    private String fieldLabel(String key) {
        if ("specialProcedure.code".equals(key)) return "code";
        if ("requestedProcedureCode".equals(key)) return "requested procedure code";
        if ("previousProcedureCode".equals(key))  return "previous procedure code";
        if ("additionalInformation.code".equals(key)) return "code";
        if ("additionalDocuments.type.code".equals(key)) return "type code";
        return key != null ? key : "field";
    }

    private String thenLineFor(String targetKey) {
        switch (targetKey) {
            case "specialProcedure":        return "Emit BR675 validation error for special procedure";
            case "requestedAndPrevious":    return "Emit BR675 validation error for requested and previous procedure";
            case "spAndAi":                 return "Emit BR675 validation error for special procedure and additional information";
            case "spAndAd":                 return "Emit BR675 validation error for special procedure and additional document";
            case "requestedAndSpecial":     return "Emit BR675 validation error for requested procedure and special procedure";
            default:                        return "Emit BR675 validation error";
        }
    }

    private String opWord(Operator op) {
        if (op == null) return "equals";
        switch (op) {
            case IN:     return "in";
            case NOT_IN: return "not in";
            default:     return "equals";
        }
    }

    private String quote(String v) {
        if (v == null || v.isBlank()) return "\"\"";
        String s = v.trim();
        boolean q = (s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"));
        return q ? s : "\"" + s + "\"";
    }
}

EmitterRegistry reg = new EmitterRegistry();
reg.register(new SpSpEmitter());
        reg.register(new RpPpEmitter());
        reg.register(new SpAiEmitter());
        reg.register(new SpAdEmitter());
        reg.register(new RpSpEmitter());
// ... any others ...
        reg.register(new DefaultFallbackEmitter());   // <— keep LAST (priority 0)
        reg.sortByPriorityDesc();


