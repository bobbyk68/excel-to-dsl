// In AtomicMatcher.java
package uk.gov.hmrc.dslgen.emit;

import java.util.*;
import java.util.regex.*;

import static java.util.Locale.ROOT;

public final class AtomicMatcher {

    private final List<CompiledAtomic> compiled;
    private final Map<String, AtomicHit> cache = new HashMap<>();

    public AtomicMatcher(List<CompiledAtomic> compiled) { this.compiled = compiled; }

    /**
     * Match a literal IF/THEN clause against JSON patterns and return an AtomicHit.
     * - If the pattern exposes (path, op, val) as groups → use them directly.
     * - If it exposes only value → derive path/op from PatternIntrospector.Meta (template DSL).
     * - For your pipeline: isIf=true for row.ifCondition(), isIf=false for row.thenCondition() (the second IF).
     */
    public AtomicHit matchAtomic(String literal, boolean isIf) {
        if (literal == null) throw new IllegalArgumentException("literal is null");
        final String norm = normalise(literal);

        AtomicHit cached = cache.get(norm);
        if (cached != null) return cached;

        for (CompiledAtomic ca : compiled) {
            Matcher m = ca.regex().matcher(norm);
            if (!m.matches()) continue;

            // collect groups (1..n) exactly as matched
            List<String> groups = new ArrayList<>(m.groupCount());
            for (int i = 1; i <= m.groupCount(); i++) {
                groups.add(nz(m.group(i)));
            }

            // Build AtomicHit using either explicit groups or Meta fallback.
            AtomicHit hit = parseUsingMeta(ca, groups, isIf, norm);
            cache.put(norm, hit);
            return hit;
        }

        throw new IllegalStateException("No atomic pattern matched literal: " + literal);
    }

    /* convenience shims matching your current call-sites */
    public AtomicHit matchIf(String literal)   { return matchAtomic(literal, true);  }
    public AtomicHit matchThen(String literal) { return matchAtomic(literal, false); }

    // ================== CORE: parse using PatternIntrospector.Meta ==================

    /**
     * Build an AtomicHit from regex groups; if the pattern didn't expose the path/op,
     * derive them from the template's Meta (anchorPath, fieldKey, operator, quantifier).
     */
    private AtomicHit parseUsingMeta(CompiledAtomic ca,
                                     List<String> groups,
                                     boolean isIf,
                                     String literal) {

        // 1) Inspect the template DSL once (no JSON changes required)
        PatternIntrospector.Meta meta = uk.gov.hmrc.dslgen.pattern.PatternIntrospector.parse(ca.dsl());

        // 2) Decide path/op/val
        String path, op, val;

        if (groups.size() >= 3) {
            // JSON provided canonical (path, op, value)
            path = groups.get(0);
            op   = groups.get(1);
            val  = groups.get(2);
        } else if (groups.size() == 2) {
            // Some patterns do (path, value) — rare, but be tolerant
            path = groups.get(0);
            op   = normaliseOp(meta.operator());  // from template
            val  = groups.get(1);
        } else if (groups.size() == 1) {
            // Your current JSON often exposes only the value → derive path/op from the template
            val  = groups.get(0);
            path = deriveCanonicalPath(meta);     // e.g. "GoodsItem.specialProcedures.code"
            op   = normaliseOp(meta.operator());  // EQ/IN/NIN from template
        } else {
            // Zero groups (shouldn’t happen) → derive everything from Meta
            path = deriveCanonicalPath(meta);
            op   = normaliseOp(meta.operator());
            val  = "";
        }

        // 3) Map path → tokens (SP/SP_CODE, AI/AI_CODE, etc.)
        var tokens = PathMapper.tokensFor(path);

        // 4) Absence modelling for your pipeline:
        //    - left IF (isIf=true) is PRESENT
        //    - right IF (isIf=false, i.e., your thenCondition) may encode ABSENT via "must ..." or "there is no ..."
        boolean isAbsence = !isIf && (meta.negated()
                                   || meta.patternKind() == PatternIntrospector.PatternKind.AT_LEAST_ONE_MUST_EQUAL
                                   || meta.patternKind() == PatternIntrospector.PatternKind.ALL_MUST_EQUAL
                                   || meta.patternKind() == PatternIntrospector.PatternKind.NONE_MUST_EQUAL);

        // If absence, force the operator token to "not exists" for the adapter/emitter,
        // but keep the value so the bullet shows the required code(s).
        String operatorToken = isAbsence ? "not exists" : normaliseOp(op);

        // 5) Materialise the AtomicHit (your concrete class already has these setters)
        AtomicHit hit = new AtomicHit();
        hit.setAnchorToken(tokens.anchorTok);   // "SP"/"AI"/"AD"/"GI"
        hit.setFieldToken(tokens.fieldTok);     // "SP_CODE"/"AI_CODE"/"REQ_PROC"/...
        hit.setOperatorToken(operatorToken);    // "equals"|"in"|"not in"|"not exists"
        hit.setRawValue(nz(val));

        // 6) Populate meta back into the hit (useful for later debugging/rules)
        hit.setAnchorScope(meta.anchorScope())
           .setAnchorPath(meta.anchorPath())
           .setFieldKey(meta.fieldKey())
           .setOperator(meta.operator())
           .setQuantifier(meta.quantifier())
           .setNegated(meta.negated())
           .setPatternKind(meta.patternKind());

        return hit;
    }

    // ================== helpers ==================

    private static String deriveCanonicalPath(PatternIntrospector.Meta meta) {
        // Try to form "GoodsItem.xxx.yyy" based on anchorPath + fieldKey; tolerate UNKNOWN field keys.
        String anchor = nz(meta.anchorPath());   // e.g. "GoodsItem.specialProcedures"
        String field  = "code";

        // Map common families to their canonical field path
        switch (anchor) {
            case "GoodsItem.specialProcedures":            field = "code"; break;
            case "GoodsItem.additionalInformation":        field = "code"; break;
            case "GoodsItem.additionalDocuments.type":     field = "code"; break;
            case "GoodsItem.additionalDocuments.exemption":field = "code"; break;
            case "GoodsItem":                              field = guessTopLevelField(meta); break; // RP/PP
            default:
                // If template carries a fieldKey we recognise, prefer it
                if (meta.fieldKey() == PatternIntrospector.FieldKey.REQUESTED_PROCEDURE) return "GoodsItem.requestedProcedureCode";
                if (meta.fieldKey() == PatternIntrospector.FieldKey.PREVIOUS_PROCEDURE)  return "GoodsItem.previousProcedureCode";
                // Fallback
        }

        if ("GoodsItem".equals(anchor)) {
            // RP/PP are modelled at GoodsItem.*ProcedureCode
            return "GoodsItem." + field;
        }
        return anchor + "." + field; // e.g. GoodsItem.specialProcedures.code
    }

    private static String guessTopLevelField(PatternIntrospector.Meta meta) {
        // Heuristic for RP/PP if meta.fieldKey was UNKNOWN
        if (meta.operator() == PatternIntrospector.Operator.RP) return "requestedProcedureCode";
        if (meta.operator() == PatternIntrospector.Operator.PP) return "previousProcedureCode";
        // default – caller will prepend GoodsItem.
        return "requestedProcedureCode";
    }

    private static String normalise(String s) {
        return s.replace('\u2013','-')
                .replace('\u2014','-')
                .replace('“','"')
                .replace('”','"')
                .replace('’','\'')
                .trim();
    }

    private static String normaliseOp(String op) {
        if (op == null) return "equals";
        String t = op.trim().toLowerCase(ROOT);
        if (t.equals("is one of"))      return "in";
        if (t.equals("is not one of"))  return "not in";
        if (t.equals("not_in"))         return "not in";
        if (t.equals("eq"))             return "equals";
        if (t.equals("in") || t.equals("not in") || t.equals("equals")) return t;
        return "equals";
    }

    private static String normaliseOp(PatternIntrospector.Operator op) {
        if (op == null) return "equals";
        switch (op) {
            case EQ:  return "equals";
            case IN:  return "in";
            case NIN: return "not in";
            default:  return "equals";
        }
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    // ---------- you already have these types; shown here just by name ----------
    public static final class CompiledAtomic {
        private final Pattern regex;
        private final String dsl;
        public CompiledAtomic(Pattern regex, String dsl) { this.regex = regex; this.dsl = dsl; }
        public Pattern regex() { return regex; }
        public String  dsl()   { return dsl; }
    }

    // Your concrete AtomicHit with fluent setters (implements AtomicHitAdapter.AtomicHit)
    public static final class AtomicHit implements AtomicHitAdapter.AtomicHit {
        private String anchorTok, fieldTok, operatorTok, rawVal;
        // meta:
        private PatternIntrospector.AnchorScope anchorScope;
        private String anchorPath;
        private PatternIntrospector.FieldKey fieldKey;
        private PatternIntrospector.Operator operator;
        private PatternIntrospector.Quantifier quantifier;
        private boolean negated;
        private PatternIntrospector.PatternKind patternKind;

        // tokens (required by adapter)
        @Override public String getAnchorToken()  { return nz(anchorTok); }
        @Override public String getFieldToken()   { return nz(fieldTok); }
        @Override public String getOperatorToken(){ return nz(operatorTok); }
        @Override public String getRawValue()     { return nz(rawVal); }

        // fluent setters used by matcher
        public AtomicHit setAnchorToken(String v){ this.anchorTok=v; return this; }
        public AtomicHit setFieldToken(String v){ this.fieldTok=v; return this; }
        public AtomicHit setOperatorToken(String v){ this.operatorTok=v; return this; }
        public AtomicHit setRawValue(String v){ this.rawVal=v; return this; }

        public AtomicHit setAnchorScope(PatternIntrospector.AnchorScope v){ this.anchorScope=v; return this; }
        public AtomicHit setAnchorPath(String v){ this.anchorPath=v; return this; }
        public AtomicHit setFieldKey(PatternIntrospector.FieldKey v){ this.fieldKey=v; return this; }
        public AtomicHit setOperator(PatternIntrospector.Operator v){ this.operator=v; return this; }
        public AtomicHit setQuantifier(PatternIntrospector.Quantifier v){ this.quantifier=v; return this; }
        public AtomicHit setNegated(boolean v){ this.negated=v; return this; }
        public AtomicHit setPatternKind(PatternIntrospector.PatternKind v){ this.patternKind=v; return this; }
    }
}
