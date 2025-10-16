// File: src/main/java/uk/gov/hmrc/dslgen/emit/PoCAllInOne.java
package uk.gov.hmrc.dslgen.emit;

import java.util.*;

/**
 * PoC: end-to-end SP–AI emission with absence (“No matching … exists”) using a single file.
 * All supporting types are static inner classes to avoid clashes in the emit package.
 */
public final class PoCAllInOne {

    /* ============================================================
     * 0) Enums
     * ============================================================ */
    static enum Operator { EQUALS, IN, NOT_IN, EXISTS, NOT_EXISTS }
    static enum HitRole  { PRIMARY, MATCHING }
    static enum Presence { PRESENT, ABSENT }   // ABSENT → “No matching … exists” / obligation unmet

    /* ============================================================
     * 1) AtomicHit (ENRICHED) — built directly after JSON regex match
     * ============================================================ */
    static interface AtomicHit {
        // Classic tokens (already used in your adapter)
        String getAnchorToken();     // "SP", "AI", "AD", "GI"
        String getFieldToken();      // "SP_CODE", "AI_CODE", "AD_TYPE_CODE", "REQ_PROC", "PREV_PROC"
        String getOperatorToken();   // "equals", "in", "not in", "not exists"
        String getRawValue();        // e.g. 72M, MOVE3, "B03"

        // New, from JSON match (no raw Excel downstream)
        String   getDslHeading();    // e.g. "Goods item with special procedure exists" (no dash)
        String   getPath();          // e.g. "GoodsItem.additionalInformation.code"
        HitRole  getRole();          // PRIMARY = first IF line; MATCHING = second IF line
        Presence getPresence();      // PRESENT/ABSENT for wording toggles
        default boolean isAbsent() { return getPresence() == Presence.ABSENT; }
    }

    /** Minimal concrete implementation you construct right after a JSON match. */
    static final class MatchedHit implements AtomicHit {
        private final String anchorToken, fieldToken, operatorToken, rawValue;
        private final String dslHeading, path;
        private final HitRole role;
        private final Presence presence;

        private MatchedHit(Builder b) {
            this.anchorToken  = b.anchorToken;
            this.fieldToken   = b.fieldToken;
            this.operatorToken= b.operatorToken;
            this.rawValue     = b.rawValue;
            this.dslHeading   = b.dslHeading;
            this.path         = b.path;
            this.role         = b.role;
            this.presence     = b.presence;
        }

        @Override public String getAnchorToken()   { return anchorToken; }
        @Override public String getFieldToken()    { return fieldToken; }
        @Override public String getOperatorToken() { return operatorToken; }
        @Override public String getRawValue()      { return rawValue; }
        @Override public String getDslHeading()    { return dslHeading; }
        @Override public String getPath()          { return path; }
        @Override public HitRole getRole()         { return role; }
        @Override public Presence getPresence()    { return presence; }

        /* Builder you call after the JSON regex has matched a line */
        static final class Builder {
            private String anchorToken, fieldToken, operatorToken, rawValue;
            private String dslHeading, path;
            private HitRole role = HitRole.PRIMARY;
            private Presence presence = Presence.PRESENT;

            Builder dslHeading(String s) { this.dslHeading = s; return this; }
            Builder path(String s)       { this.path = s; return this; }
            Builder role(HitRole r)      { this.role = r; return this; }
            Builder presence(Presence p) { this.presence = p; return this; }

            /** The four classic tokens: anchor/field/op/value */
            Builder tokens(String anchorTok, String fieldTok, String opTok, String value) {
                this.anchorToken   = nn(anchorTok);
                this.fieldToken    = nn(fieldTok);
                this.operatorToken = normaliseOp(opTok);
                this.rawValue      = value == null ? "" : value.trim();
                return this;
            }

            MatchedHit build() {
                if (dslHeading == null) throw new IllegalStateException("dslHeading required");
                if (anchorToken == null || fieldToken == null || operatorToken == null)
                    throw new IllegalStateException("tokens(anchor/field/op) required");
                return new MatchedHit(this);
            }

            private static String nn(String s){ return s == null ? "" : s.trim(); }
            private static String normaliseOp(String s){
                if (s == null) return "equals";
                String t = s.trim().toLowerCase(Locale.ROOT);
                if (t.equals("in")) return "in";
                if (t.equals("not in") || t.equals("not_in")) return "not in";
                if (t.equals("not exists") || t.equals("nexists")) return "not exists";
                return "equals";
            }
        }
    }

    /* ============================================================
     * 2) Condition (pkg-private vibe) + public-style factory
     * ============================================================ */
    static final class Condition {
        private final String anchorKey;    // e.g. "GoodsItem.additionalInformation"
        private final String fieldKey;     // e.g. "additionalInformation.code"
        private final Operator operator;   // EQUALS/IN/NOT_IN/NOT_EXISTS
        private final String displayValue; // quoted string or csv of quoted

        Condition(String anchorKey, String fieldKey, Operator operator, String displayValue) {
            this.anchorKey = anchorKey;
            this.fieldKey  = fieldKey;
            this.operator  = operator;
            this.displayValue = displayValue;
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
    static final class Conditions {
        private Conditions(){}
        static Condition of(String anchor, String field, Operator op, String value) {
            return new Condition(anchor, field, op, value);
        }
    }

    /* ============================================================
     * 3) AtomicHit → Condition adapter (alias maps & quoting)
     * ============================================================ */
    static final class AtomicHitAdapter {
        private AtomicHitAdapter(){}

        static Condition toCondition(AtomicHit hit) {
            String anchorKey = mapAnchor(hit.getAnchorToken());
            String fieldKey  = mapField(hit.getFieldToken());
            Operator op      = mapOperator(hit.getOperatorToken());
            String value     = normaliseValue(op, hit.getRawValue());
            return Conditions.of(anchorKey, fieldKey, op, value);
        }

        private static final Map<String,String> ANCHOR_MAP = Map.of(
            "SP", "GoodsItem.specialProcedures",
            "AI", "GoodsItem.additionalInformation",
            "AD", "GoodsItem.additionalDocuments",
            "GI", "GoodsItem"
        );
        private static final Map<String,String> FIELD_MAP = Map.ofEntries(
            Map.entry("SP_CODE",      "specialProcedure.code"),
            Map.entry("AI_CODE",      "additionalInformation.code"),
            Map.entry("AD_TYPE_CODE", "additionalDocuments.type.code"),
            Map.entry("REQ_PROC",     "requestedProcedureCode"),
            Map.entry("PREV_PROC",    "previousProcedureCode"),
            // canonical passthroughs
            Map.entry("specialProcedure.code",          "specialProcedure.code"),
            Map.entry("additionalInformation.code",     "additionalInformation.code"),
            Map.entry("additionalDocuments.type.code",  "additionalDocuments.type.code"),
            Map.entry("requestedProcedureCode",         "requestedProcedureCode"),
            Map.entry("previousProcedureCode",          "previousProcedureCode")
        );

        private static String mapAnchor(String t) {
            if (t == null) return "GoodsItem";
            String k = ANCHOR_MAP.get(t.trim().toUpperCase(Locale.ROOT));
            return (k != null) ? k : "GoodsItem";
        }
        private static String mapField(String t) {
            if (t == null) return "unknown";
            String key = FIELD_MAP.get(t.trim());
            if (key != null) return key;
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
            if (s.contains(",") && (s.contains("\"") || s.contains("'"))) return s; // already-quoted CSV
            if (s.contains(",")) { // unquoted CSV → quote each
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
     * 4) EmitContext (+ ThenPart) & normaliser (promotion + inference)
     * ============================================================ */
    static final class ThenPart { private final String key; ThenPart(String k){ this.key=k; } String targetKey(){ return key; } }

    static final class EmitContext {
        private final String parentAnchor;
        private final List<Condition> conditions; // ordered: [left(primary), right(matching?)]
        private final ThenPart thenPart;

        EmitContext(String parentAnchor, List<Condition> conditions, ThenPart thenPart) {
            this.parentAnchor = parentAnchor;
            this.conditions   = conditions;
            this.thenPart     = thenPart;
        }
        String anchorKey()            { return parentAnchor; }
        List<Condition> conditions()  { return conditions; }
        ThenPart thenPart()           { return thenPart; }
    }

    static final class EmitContextFactory {
        private EmitContextFactory(){}

        /** Main entry: shape the pair BEFORE emitter selection */
        static EmitContext fromHits(AtomicHit leftHit, AtomicHit rightHit, AtomicHit thenHit) {
            Condition left  = AtomicHitAdapter.toCondition(leftHit);
            Condition right = (rightHit != null) ? AtomicHitAdapter.toCondition(rightHit) : null;

            List<Condition> conds = new ArrayList<>();
            conds.add(left);

            // PROMOTION: if IF has only one side & THEN is concrete → use THEN as the matching (right) condition
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

        private static boolean isConcrete(AtomicHit hit) {
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
        private static String deriveThenTargetKey(AtomicHit thenHit, List<Condition> conds) {
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
     * 5) Writer & Emitter SPI
     * ============================================================ */
    static final class DslrFileWriter {
        private final StringBuffer buf = new StringBuffer();
        private boolean whenOpened = false, thenOpened = false;

        void whenLine(String line) { if (!whenOpened) { buf.append("[when]\n"); whenOpened = true; } buf.append("    ").append(line).append("\n"); }
        void thenLine(String line) { if (!thenOpened) { buf.append("[then]\n"); thenOpened = true; } buf.append("    ").append(line).append("\n"); }
        String getText() { return buf.toString(); }
        void reset() { buf.setLength(0); whenOpened=false; thenOpened=false; }
    }

    static interface Emitter {
        int priority();
        boolean canHandle(EmitContext ctx);
        void emit(EmitContext ctx, DslrFileWriter dsl);
    }
    static final class EmitterRegistry {
        private final List<Emitter> emitters = new ArrayList<>();
        void register(Emitter e){ emitters.add(e); }
        void sortByPriorityDesc(){ emitters.sort((a,b)->Integer.compare(b.priority(), a.priority())); }
        boolean dispatch(EmitContext ctx, DslrFileWriter dsl){
            for (Emitter e : emitters) if (e.canHandle(ctx)) { e.emit(ctx, dsl); return true; }
            return false;
        }
    }

    /* ============================================================
     * 6) Specific emitter: SP–AI (presence + absence wording)
     * ============================================================ */
    static final class SpAiEmitter implements Emitter {
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

            Operator showOp = isAbsent ? Operator.EQUALS : right.operator(); // show criterion even for NOT_EXISTS
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

    /* ============================================================
     * 7) Fallback emitter (kept minimal)
     * ============================================================ */
    static final class DefaultFallbackEmitter implements Emitter {
        @Override public int priority() { return 0; }
        @Override public boolean canHandle(EmitContext ctx) {
            return ctx != null && ctx.conditions()!=null && !ctx.conditions().isEmpty();
        }
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
            dsl.thenLine("Emit BR675 validation error");
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
    }

    /* ============================================================
     * 8) DEMO main (simulates JSON→Hit→normalise→emit)
     * ============================================================ */
    public static void main(String[] args) {
        // Simulated JSON matches (normally built right after regex matches)
        // IF #1 (PRIMARY, PRESENT): SP.code equals "72M"
        AtomicHit left = new MatchedHit.Builder()
            .dslHeading("Goods item with special procedure exists")              // from JSON "dsl"
            .path("GoodsItem.specialProcedures.code")                            // from regex group
            .role(HitRole.PRIMARY)
            .presence(Presence.PRESENT)
            .tokens("SP", "SP_CODE", "equals", "72M")                            // from groups & path→token map
            .build();

        // THEN obligation promoted to IF #2 (MATCHING, ABSENT): AI.code must equals MOVE3
        AtomicHit thenAsMatch = new MatchedHit.Builder()
            .dslHeading("Matching goods item with additional information exists")// emitter will flip to “No matching …” if ABSENT
            .path("GoodsItem.additionalInformation.code")
            .role(HitRole.MATCHING)
            .presence(Presence.ABSENT)                                           // obligation unmet → ABSENT
            .tokens("AI", "AI_CODE", "not exists", "MOVE3")
            .build();

        // Build context (promotion occurs here if you pass null for a right IF hit)
        EmitContext ctx = EmitContextFactory.fromHits(left, null, thenAsMatch);

        // Registry
        EmitterRegistry reg = new EmitterRegistry();
        reg.register(new SpAiEmitter());             // specific
        reg.register(new DefaultFallbackEmitter());  // fallback last
        reg.sortByPriorityDesc();

        // Emit
        DslrFileWriter dsl = new DslrFileWriter();
        boolean handled = reg.dispatch(ctx, dsl);

        // Minimal rule envelope (for quick visual)
        String rule = """
            rule "BR675_PoC"
            @ErrorCode("DMS12056")
            @declarationType("J,F,C")
            @procedureCategory("C211,C21E")
            %s
            end
            """.formatted(handled ? dsl.getText() : "[when]\n    [no emitter handled]\n");

        System.out.println(rule);
    }
}
