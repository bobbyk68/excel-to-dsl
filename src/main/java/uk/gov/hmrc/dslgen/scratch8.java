package uk.gov.hmrc.dslgen.emit;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* ============================================================
 * Core domain
 * ============================================================ */
enum Operator { EQUALS, IN, NOT_IN, EXISTS, NOT_EXISTS }

final class Condition {
    private final String anchorKey;    // e.g. "GoodsItem.specialProcedures"
    private final String fieldKey;     // e.g. "specialProcedure.code"
    private final Operator operator;   // e.g. EQUALS | IN | NOT_EXISTS
    private final String displayValue; // e.g. "72M" or "\"B03\""

    Condition(String anchorKey, String fieldKey, Operator operator, String displayValue) {
        this.anchorKey = anchorKey; this.fieldKey = fieldKey; this.operator = operator; this.displayValue = displayValue;
    }
    String anchorKey()    { return anchorKey; }
    String fieldKey()     { return fieldKey; }
    Operator operator()   { return operator; }
    String displayValue() { return displayValue; }

    String semanticKey() {
        return (anchorKey == null ? "" : anchorKey) + "|" +
                (fieldKey  == null ? "" : fieldKey)  + "|" +
                (operator  == null ? "?" : operator.name()) + "|" +
                (displayValue == null ? "" : displayValue.trim());
    }
}

final class ThenPart {
    private final String targetKey; // e.g., "spAndAi"
    ThenPart(String targetKey) { this.targetKey = targetKey; }
    String targetKey() { return targetKey; }
}

final class EmitContext {
    private final String parentAnchor;        // e.g., "GoodsItem"
    private final List<Condition> conditions; // size 1 or 2
    private final ThenPart thenPart;

    EmitContext(String parentAnchor, List<Condition> conditions, ThenPart thenPart) {
        this.parentAnchor = parentAnchor; this.conditions = conditions; this.thenPart = thenPart;
    }
    String anchorKey() { return parentAnchor; }
    List<Condition> conditions() { return conditions; }
    ThenPart thenPart() { return thenPart; }
}

/* ============================================================
 * Writer
 * ============================================================ */
final class DslrFileWriter {
    private final StringBuffer buf = new StringBuffer();
    private boolean whenOpened = false, thenOpened = false;

    void whenLine(String line) {
        if (!whenOpened) { buf.append("[when]\n"); whenOpened = true; }
        buf.append("    ").append(line).append("\n");
    }
    void thenLine(String line) {
        if (!thenOpened) { buf.append("[then]\n"); thenOpened = true; }
        buf.append("    ").append(line).append("\n");
    }
    String getText() { return buf.toString(); }
    void reset() { buf.setLength(0); whenOpened = false; thenOpened = false; }
}

/* ============================================================
 * SPI
 * ============================================================ */
interface Emitter {
    int priority();
    boolean canHandle(EmitContext ctx);
    void emit(EmitContext ctx, DslrFileWriter dsl);
}

final class EmitterRegistry {
    private final List<Emitter> emitters = new ArrayList<>();
    void register(Emitter e){ emitters.add(e); }
    void sortByPriorityDesc(){ emitters.sort((a,b)->Integer.compare(b.priority(), a.priority())); }
    boolean dispatch(EmitContext ctx, DslrFileWriter dsl){
        for (Emitter e : emitters) if (e.canHandle(ctx)) { e.emit(ctx, dsl); return true; }
        return false;
    }
}

/* ============================================================
 * Adapter (AtomicHit -> Condition)
 * ============================================================ */
final class AtomicHitAdapter {
    private AtomicHitAdapter(){}

    interface AtomicHit {
        String getAnchorToken();   // e.g., "SP","AI","AD","GI"
        String getFieldToken();    // e.g., "SP_CODE","AI_CODE","REQ_PROC","PREV_PROC","AD_TYPE_CODE"
        String getOperatorToken(); // e.g., "equals","in","not in","not exists"
        String getRawValue();      // e.g., 72M or MOVE3 or "B03"
    }

    static Condition toCondition(AtomicHit hit) {
        String anchorKey = mapAnchor(hit.getAnchorToken());
        String fieldKey  = mapField(hit.getFieldToken());
        Operator op      = mapOperator(hit.getOperatorToken());
        String value     = normaliseValue(op, hit.getRawValue());
        return new Condition(anchorKey, fieldKey, op, value);
    }

    private static final Map<String,String> ANCHOR_MAP = Map.of(
            "SP", "GoodsItem.specialProcedures",
            "AI", "GoodsItem.additionalInformation",
            "AD", "GoodsItem.additionalDocuments",
            "GI", "GoodsItem"
    );

    private static final Map<String,String> FIELD_MAP = Map.of(
            "SP_CODE",      "specialProcedure.code",
            "AI_CODE",      "additionalInformation.code",
            "AD_TYPE_CODE", "additionalDocuments.type.code",
            "REQ_PROC",     "requestedProcedureCode",
            "PREV_PROC",    "previousProcedureCode"
    );

    private static String mapAnchor(String t) {
        if (t == null) return "GoodsItem";
        String k = ANCHOR_MAP.get(t.trim().toUpperCase(Locale.ROOT));
        return k != null ? k : "GoodsItem";
    }
    private static String mapField(String t) {
        if (t == null) return "unknown";
        String k = FIELD_MAP.get(t.trim().toUpperCase(Locale.ROOT));
        if (k != null) return k;
        if (t.contains(".")) return t;
        return t;
    }
    private static Operator mapOperator(String token) {
        String t = token == null ? "equals" : token.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "in": return Operator.IN;
            case "not in": return Operator.NOT_IN;
            case "exists": return Operator.EXISTS;
            case "not exists":
            case "nexists": return Operator.NOT_EXISTS;
            default: return Operator.EQUALS;
        }
    }
    private static String normaliseValue(Operator op, String raw) {
        if (op == Operator.EXISTS || op == Operator.NOT_EXISTS) return raw == null ? "" : raw.trim();
        if (raw == null || raw.isBlank()) return "\"\"";
        String s = raw.trim();
        // Already quoted CSV
        if (s.contains(",") && (s.contains("\"") || s.contains("'"))) return s;
        // Unquoted CSV -> quote each
        if (s.contains(",")) {
            String[] parts = s.split(",");
            for (int i=0;i<parts.length;i++) parts[i] = quoteIfNeeded(parts[i].trim());
            return String.join(",", parts);
        }
        return quoteIfNeeded(s);
    }
    private static String quoteIfNeeded(String v) {
        if (v == null || v.isBlank()) return "\"\"";
        String s = v.trim();
        boolean q = (s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"));
        return q ? s : "\"" + s + "\"";
    }
}

/* ============================================================
 * RuleRow (existing names preserved) + Adapters
 * ============================================================ */
final class RuleRow {
    private final String id, ifCondition, thenCondition, errorCode;
    private final List<String> declarationType, procedureCategory;

    RuleRow(String id, String ifCondition, String thenCondition, String errorCode,
            List<String> declarationType, List<String> procedureCategory) {
        this.id = id; this.ifCondition = ifCondition; this.thenCondition = thenCondition; this.errorCode = errorCode;
        this.declarationType = declarationType; this.procedureCategory = procedureCategory;
    }
    String id() { return id; }
    String ifCondition() { return ifCondition; }
    String thenCondition() { return thenCondition; }
    String errorCode() { return errorCode; }
    List<String> declarationType() { return declarationType; }
    List<String> procedureCategory() { return procedureCategory; }
}

final class RuleRowAdapters {
    private RuleRowAdapters(){}

    // ===== IF parser (returns up to two hits; SP–AI if both present in IF) =====

    static Pair parseIf(String ifText) {
        if (ifText == null) return new Pair(null, null);

        AtomicHit hitL = null, hitR = null;
        String currentAnchor = null;

        String[] lines = ifText.replace("\r","").split("\n");
        for (String raw : lines) {
            String line = raw.trim().toLowerCase(Locale.ROOT);
            if (line.isBlank()) continue;

            // headings / scope lines
            if (line.contains("special procedure"))           currentAnchor = "SP";
            if (line.contains("additional information"))      currentAnchor = "AI";
            if (line.contains("additional document"))         currentAnchor = "AD";

            // bullets
            Matcher mSp = SP_LINE.matcher(line);
            if (mSp.find() && "SP".equals(currentAnchor)) {
                AtomicHit h = new Hit("SP","SP_CODE", mSp.group(1), mSp.group(2).trim());
                if (hitL == null) hitL = h; else if (hitR == null) hitR = h;
                continue;
            }
            Matcher mAi = AI_LINE.matcher(line);
            if (mAi.find() && "AI".equals(currentAnchor)) {
                AtomicHit h = new Hit("AI","AI_CODE", mAi.group(1), mAi.group(2).trim());
                if (hitL == null) hitL = h; else if (hitR == null) hitR = h;
                continue;
            }
            Matcher mAd = AD_LINE.matcher(line);
            if (mAd.find() && "AD".equals(currentAnchor)) {
                AtomicHit h = new Hit("AD","AD_TYPE_CODE", mAd.group(1), mAd.group(2).trim());
                if (hitL == null) hitL = h; else if (hitR == null) hitR = h;
                continue;
            }
            Matcher mRp = RP_LINE.matcher(line);
            if (mRp.find()) {
                AtomicHit h = new Hit("GI","REQ_PROC", mRp.group(1), mRp.group(2).trim());
                if (hitL == null) hitL = h; else if (hitR == null) hitR = h;
                continue;
            }
            Matcher mPp = PP_LINE.matcher(line);
            if (mPp.find()) {
                AtomicHit h = new Hit("GI","PREV_PROC", mPp.group(1), mPp.group(2).trim());
                if (hitL == null) hitL = h; else if (hitR == null) hitR = h;
            }
        }
        return new Pair(hitL, hitR);
    }

    public static AtomicHitAdapter.AtomicHit left(RuleRow row)  { return parseIf(row.ifCondition()).left; }
    public static AtomicHitAdapter.AtomicHit right(RuleRow row) { return parseIf(row.ifCondition()).right; }

    // ===== THEN parser: value-bearing requirements (promotion candidates) =====
    public static AtomicHitAdapter.AtomicHit then(RuleRow row) {
        String s = row.thenCondition();
        if (s == null || s.isBlank()) return null;

        String compact = s.replace(" ", "");
        // AI.code must equals MOVE3 -> NOT_EXISTS (obligation missing => "No matching ..." wording)
        Matcher ai = Pattern.compile("additionalinformation\\.code(?:must)?equals(.+)", Pattern.CASE_INSENSITIVE).matcher(compact);
        if (ai.find()) return new Hit("AI","AI_CODE","not exists", ai.group(1).trim());

        // SP.code must equals 72M -> NOT_EXISTS on SP (rare but symmetrical)
        Matcher sp = Pattern.compile("specialprocedures\\.code(?:must)?equals(.+)", Pattern.CASE_INSENSITIVE).matcher(compact);
        if (sp.find()) return new Hit("SP","SP_CODE","not exists", sp.group(1).trim());

        // AD.type.code must equals C501 -> NOT_EXISTS
        Matcher ad = Pattern.compile("additionaldocuments\\.type\\.code(?:must)?equals(.+)", Pattern.CASE_INSENSITIVE).matcher(compact);
        if (ad.find()) return new Hit("AD","AD_TYPE_CODE","not exists", ad.group(1).trim());

        // else: symbolic (kept for error bucket inference)
        String low = s.toLowerCase(Locale.ROOT);
        String tok = low.contains("requested") && low.contains("previous") ? "RP_PP"
                : (low.contains("special procedure") && low.contains("additional information")) ? "SP_AI"
                : (low.contains("special procedure") && low.contains("additional document")) ? "SP_AD"
                : (low.contains("special procedure") ? "SP" : "VALIDATION");
        return new Hit("", tok, "", "");
    }

    // ==== regex (bullets) ====
    private static final Pattern SP_LINE = Pattern.compile("-\\s*with\\s*code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern AI_LINE = Pattern.compile("-\\s*with\\s*code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern AD_LINE = Pattern.compile("-\\s*with\\s*(?:type\\s*)?code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern RP_LINE = Pattern.compile("-\\s*with\\s*requested\\s*procedure\\s*code\\s*(equals|in|not in)\\s*(.+)");
    private static final Pattern PP_LINE = Pattern.compile("-\\s*with\\s*previous\\s*procedure\\s*code\\s*(equals|in|not in)\\s*(.+)");

    // ==== light-weight AtomicHit & Pair ====
    private record Hit(String a, String f, String o, String v) implements AtomicHitAdapter.AtomicHit {
        public String getAnchorToken()   { return a; }
        public String getFieldToken()    { return f; }
        public String getOperatorToken() { return o; }
        public String getRawValue()      { return v; }
    }
    private static final class Pair {
        final AtomicHitAdapter.AtomicHit left, right;
        Pair(AtomicHitAdapter.AtomicHit l, AtomicHitAdapter.AtomicHit r) { this.left = l; this.right = r; }
    }
}

/* ============================================================
 * Context factory (promotion + anchoring + target inference)
 * ============================================================ */
final class EmitContextFactory {
    private EmitContextFactory(){}

    static EmitContext fromRow(RuleRow row) {
        var leftHit  = RuleRowAdapters.left(row);
        var rightHit = RuleRowAdapters.right(row);
        var thenHit  = RuleRowAdapters.then(row); // may be null or symbolic

        Condition left  = AtomicHitAdapter.toCondition(leftHit);
        Condition right = (rightHit != null) ? AtomicHitAdapter.toCondition(rightHit) : null;

        List<Condition> conds = new ArrayList<>();
        conds.add(left);

        // PROMOTION: if only one IF condition & THEN is concrete → use it as the matching condition
        if (right == null && isConcrete(thenHit)) {
            conds.add(AtomicHitAdapter.toCondition(thenHit));
        } else if (right != null) {
            conds.add(right);
        }

        conds = dedupe(conds);
        String parent = deriveParentAnchor(conds);
        String thenKey = deriveThenTargetKey(thenHit, conds);

        return new EmitContext(parent, conds, new ThenPart(thenKey));
    }

    private static boolean isConcrete(AtomicHitAdapter.AtomicHit hit) {
        if (hit == null) return false;
        String f = hit.getFieldToken();
        if (f == null || f.isBlank()) return false;
        String u = f.trim().toUpperCase(Locale.ROOT);
        return !Set.of("VALIDATION","RP_PP","SP_AI","SP_AD","SP").contains(u);
    }

    private static List<Condition> dedupe(List<Condition> in) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Condition> out = new ArrayList<>();
        for (Condition c : in) if (seen.add(c.semanticKey())) out.add(c);
        return out;
    }

    private static String deriveParentAnchor(List<Condition> conds) {
        if (conds.isEmpty()) return "Unknown";
        boolean allGoods = conds.stream().allMatch(c -> c.anchorKey()!=null && c.anchorKey().startsWith("GoodsItem"));
        if (allGoods) return "GoodsItem";
        String first = Optional.ofNullable(conds.get(0).anchorKey()).orElse("Unknown");
        int dot = first.indexOf('.');
        return dot >= 0 ? first.substring(0, dot) : first;
    }

    private static String deriveThenTargetKey(AtomicHitAdapter.AtomicHit thenHit, List<Condition> conds) {
        if (thenHit != null) {
            String t = Optional.ofNullable(thenHit.getFieldToken()).orElse("").trim().toUpperCase(Locale.ROOT);
            switch (t) {
                case "RP_PP": return "requestedAndPrevious";
                case "SP_AI": return "spAndAi";
                case "SP_AD": return "spAndAd";
                case "SP":    return "specialProcedure";
            }
        }
        // infer from IF composition
        boolean hasSP = conds.stream().anyMatch(c -> "specialProcedure.code".equals(c.fieldKey()));
        boolean hasAI = conds.stream().anyMatch(c -> "additionalInformation.code".equals(c.fieldKey()));
        boolean hasAD = conds.stream().anyMatch(c -> "additionalDocuments.type.code".equals(c.fieldKey()));
        boolean hasRP = conds.stream().anyMatch(c -> "requestedProcedureCode".equals(c.fieldKey()));
        boolean hasPP = conds.stream().anyMatch(c -> "previousProcedureCode".equals(c.fieldKey()));
        if (hasSP && hasAI) return "spAndAi";
        if (hasSP && hasAD) return "spAndAd";
        if (hasRP && hasPP) return "requestedAndPrevious";
        if (hasSP)          return "specialProcedure";
        return "validation";
    }
}

/* ============================================================
 * Emitters
 * ============================================================ */
final class SpAiEmitter implements Emitter {
    @Override public int priority() { return 200; }

    @Override public boolean canHandle(EmitContext ctx) {
        if (ctx == null || ctx.conditions() == null || ctx.conditions().size() < 2) return false;
        Condition left  = ctx.conditions().get(0);
        Condition right = ctx.conditions().get(1);
        return left.anchorKey().startsWith("GoodsItem.specialProcedures")
                && right.anchorKey().startsWith("GoodsItem.additionalInformation")
                && "specialProcedure.code".equals(left.fieldKey())
                && "additionalInformation.code".equals(right.fieldKey());
    }

    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        Condition left  = ctx.conditions().get(0);
        Condition right = ctx.conditions().get(1);
        boolean isAbsent = right.operator() == Operator.NOT_EXISTS;

        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code " + opWord(left.operator()) + " " + quote(left.displayValue()));

        dsl.whenLine(isAbsent
                ? "No matching goods item with additional information exists"
                : "Matching goods item with additional information exists");

        // for NOT_EXISTS we still show the criterion (equals/in) as a bullet
        Operator showOp = (right.operator() == Operator.NOT_EXISTS) ? Operator.EQUALS : right.operator();
        dsl.whenLine("- with code " + opWord(showOp) + " " + quote(right.displayValue()));

        dsl.thenLine(isAbsent
                ? "Emit BR675 validation error for special procedure and no additional information"
                : "Emit BR675 validation error for special procedure and additional information");
    }

    private String opWord(Operator op) {
        if (op == null) return "equals";
        switch (op) { case IN: return "in"; case NOT_IN: return "not in"; default: return "equals"; }
    }
    private String quote(String v) {
        if (v == null || v.isBlank()) return "\"\"";
        String s = v.trim();
        boolean q = (s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"));
        return q ? s : "\"" + s + "\"";
    }
}

final class DefaultFallbackEmitter implements Emitter {
    @Override public int priority() { return 0; }
    @Override public boolean canHandle(EmitContext ctx) { return ctx != null && ctx.conditions()!=null && !ctx.conditions().isEmpty(); }
    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        List<Condition> c = ctx.conditions();
        Condition c1 = c.get(0);
        dsl.whenLine(primaryExistsText(c1.anchorKey()));
        dsl.whenLine("- with " + fieldLabel(c1.fieldKey()) + " " + opWord(c1.operator()) + " " + quote(c1.displayValue()));
        if (c.size() > 1) {
            Condition c2 = c.get(1);
            boolean absent = c2.operator() == Operator.NOT_EXISTS;
            dsl.whenLine(absent ? "No matching " + anchorNoun(c2.anchorKey()) + " exists"
                    : "Matching " + anchorNoun(c2.anchorKey()) + " exists");
            Operator showOp = absent ? Operator.EQUALS : c2.operator();
            dsl.whenLine("- with " + fieldLabel(c2.fieldKey()) + " " + opWord(showOp) + " " + quote(c2.displayValue()));
        }
        String target = ctx.thenPart()!=null ? ctx.thenPart().targetKey() : "validation";
        dsl.thenLine(targetLine(target, c.size() > 1 && c.get(1).operator()==Operator.NOT_EXISTS));
    }
    private String primaryExistsText(String anchor) {
        if (anchor != null && anchor.startsWith("GoodsItem.specialProcedures")) return "Goods item with special procedure exists";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalInformation")) return "Goods item with additional information exists";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalDocuments")) return "Goods item with additional document exists";
        return "Goods item exists";
    }
    private String anchorNoun(String anchor) {
        if (anchor != null && anchor.startsWith("GoodsItem.specialProcedures")) return "goods item with special procedure";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalInformation")) return "goods item with additional information";
        if (anchor != null && anchor.startsWith("GoodsItem.additionalDocuments")) return "goods item with additional document";
        return "goods item";
    }
    private String fieldLabel(String k) {
        if ("specialProcedure.code".equals(k)) return "code";
        if ("additionalInformation.code".equals(k)) return "code";
        if ("additionalDocuments.type.code".equals(k)) return "type code";
        if ("requestedProcedureCode".equals(k)) return "requested procedure code";
        if ("previousProcedureCode".equals(k))  return "previous procedure code";
        return k != null ? k : "field";
    }
    private String opWord(Operator op) {
        if (op == null) return "equals";
        switch (op) { case IN: return "in"; case NOT_IN: return "not in"; default: return "equals"; }
    }
    private String quote(String v) {
        if (v == null || v.isBlank()) return "\"\"";
        String s=v.trim(); boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'"));
        return q? s : "\""+s+"\"";
    }
    private String targetLine(String key, boolean absent) {
        switch (key) {
            case "spAndAi": return absent
                    ? "Emit BR675 validation error for special procedure and no additional information"
                    : "Emit BR675 validation error for special procedure and additional information";
            case "specialProcedure": return "Emit BR675 validation error for special procedure";
            case "requestedAndPrevious": return "Emit BR675 validation error for requested and previous procedure";
            default: return "Emit BR675 validation error";
        }
    }
}

/* ============================================================
 * Demo (public class so file compiles)
 * ============================================================ */
public final class DemoMain {
    public static void main(String[] args) {
        // Example row (your screenshot): IF has SP; THEN carries AI obligation
        RuleRow row = new RuleRow(
                "BR675_1231",
                """
                Goods item with special procedure exists
                - with code equals "72M"
                """,
                // THEN: obligation on AI that should render "No matching ..."
                "at least one GoodsItem.additionalInformation.code must equals MOVE3",
                "DMS12056",
                List.of("J","F","C"),
                List.of("C211","C21E")
        );

        // Registry
        EmitterRegistry reg = new EmitterRegistry();
        reg.register(new SpAiEmitter());            // specific
        reg.register(new DefaultFallbackEmitter()); // last
        reg.sortByPriorityDesc();

        // Build context (promotion happens here), then emit
        EmitContext ctx = EmitContextFactory.fromRow(row);
        DslrFileWriter dsl = new DslrFileWriter();
        boolean handled = reg.dispatch(ctx, dsl);

        // Wrap with the rule envelope (simple)
        String dslr = """
            rule "%s"
            @ErrorCode("%s")
            @declarationType("%s")
            @procedureCategory("%s")
            %s
            end
            """.formatted(
                row.id(),
                row.errorCode(),
                String.join(",", row.declarationType()),
                String.join(",", row.procedureCategory()),
                handled ? dsl.getText() : "[when]\n    [no emitter handled]\n"
        );

        System.out.println(dslr);
    }
}
