package uk.gov.hmrc.dslgen.pattern;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Introspects a DSLR/phrasebook template (e.g. "at least one GoodsItem.specialProcedures.code must equals {1}")
 * and classifies it into structured metadata used by the matcher/emitter pipeline.
 */
public final class PatternIntrospector {

    private PatternIntrospector() {}

    // ===== Public API =====
    public static Meta parse(String dslTemplate) {
        if (dslTemplate == null) throw new IllegalArgumentException("dslTemplate is null");
        String raw = dslTemplate.trim();
        String text = normalise(dslTemplate);

        // ---- Quantifier / Negation / Kind (from English scaffolding) ----
        Quantifier quantifier = detectQuantifier(text);
        boolean negated = detectNegated(text);
        Operator opFromWords = detectOperatorFromWords(text); // equals | in | not in | neq
        PatternKind kind = detectPatternKind(text, quantifier, negated, opFromWords);

        // ---- Path & Field detection ----
        PathParts pp = detectPathParts(raw); // use raw to preserve case/dots
        // If we couldn't detect a full path, try fallback word-based heuristics
        if (pp == null) {
            pp = heuristicPathFromWords(text);
        }

        // ---- Operator finalisation ----
        Operator operator = opFromWords != null ? opFromWords
                : (kind.impliesAbsence() ? Operator.EQ : Operator.EQ); // default EQ

        // ---- Anchor scope from anchorPath ----
        AnchorScope scope = scopeFromAnchor(pp != null ? pp.anchorPath : null);

        // Build Meta
        return new Meta(scope,
                pp == null ? "GoodsItem" : pp.anchorPath,
                pp == null ? FieldKey.UNKNOWN : pp.fieldKey,
                operator,
                quantifier,
                negated,
                kind);
    }

    // ===== Enums =====
    public enum AnchorScope { GI, SP, AI, AD, AD_EXEMPTION, GI_RP, GI_PP, UNKNOWN }
    public enum FieldKey {
        SPECIAL_PROCEDURE_CODE,
        ADDITIONAL_INFORMATION_CODE,
        ADDITIONAL_DOCUMENT_TYPE_CODE,
        ADDITIONAL_DOCUMENT_EXEMPTION_CODE,
        REQUESTED_PROCEDURE,
        PREVIOUS_PROCEDURE,
        UNKNOWN
    }
    public enum Operator { EQ, IN, NIN, NEQ }
    public enum Quantifier { EXISTS, ALL }
    public enum PatternKind {
        EXISTS,                       // "there is at least one ... equals ..."
        NONE_EXISTS,                  // "there is no ..."
        AT_LEAST_ONE_MUST_EQUAL,      // "at least one ... must equals ..."
        ALL_MUST_EQUAL,               // "all ... must equals/is one of ..."
        NONE_MUST_EQUAL;              // "none ... must equals" / prohibition

        public boolean impliesAbsence() {
            return this == NONE_EXISTS || this == AT_LEAST_ONE_MUST_EQUAL || this == NONE_MUST_EQUAL;
        }
    }

    // ===== Result model =====
    public static final class Meta {
        private final AnchorScope anchorScope;
        private final String anchorPath;
        private final FieldKey fieldKey;
        private final Operator operator;
        private final Quantifier quantifier;
        private final boolean negated;
        private final PatternKind patternKind;

        public Meta(AnchorScope scope, String anchorPath, FieldKey fk, Operator op,
                    Quantifier q, boolean negated, PatternKind kind) {
            this.anchorScope = scope == null ? AnchorScope.UNKNOWN : scope;
            this.anchorPath = anchorPath == null ? "GoodsItem" : anchorPath;
            this.fieldKey = fk == null ? FieldKey.UNKNOWN : fk;
            this.operator = op == null ? Operator.EQ : op;
            this.quantifier = q == null ? Quantifier.EXISTS : q;
            this.negated = negated;
            this.patternKind = kind == null ? PatternKind.EXISTS : kind;
        }

        public AnchorScope anchorScope() { return anchorScope; }
        public String anchorPath() { return anchorPath; }
        public FieldKey fieldKey() { return fieldKey; }
        public Operator operator() { return operator; }
        public Quantifier quantifier() { return quantifier; }
        public boolean negated() { return negated; }
        public PatternKind patternKind() { return patternKind; }

        @Override public String toString() {
            return "Meta{scope=" + anchorScope + ", path=" + anchorPath + ", fieldKey=" + fieldKey +
                    ", op=" + operator + ", q=" + quantifier + ", negated=" + negated +
                    ", kind=" + patternKind + "}";
        }
    }

    // ===== Detection logic =====

    private static Quantifier detectQuantifier(String t) {
        if (W_ALL.matcher(t).find()) return Quantifier.ALL;
        return Quantifier.EXISTS; // default for "there is ...", "at least one ..."
    }

    private static boolean detectNegated(String t) {
        // explicit "there is no ..." or "no matching ..." or "must not ..."
        if (W_THERE_IS_NO.matcher(t).find()) return true;
        if (W_NO_MATCHING.matcher(t).find()) return true;
        if (W_MUST_NOT.matcher(t).find()) return true;
        return false;
    }

    private static Operator detectOperatorFromWords(String t) {
        if (W_IS_NOT_ONE_OF.matcher(t).find()) return Operator.NIN;
        if (W_NOT_IN.matcher(t).find())        return Operator.NIN;
        if (W_IS_ONE_OF.matcher(t).find())     return Operator.IN;
        if (W_IN.matcher(t).find())            return Operator.IN;
        if (W_NOT_EQUALS.matcher(t).find())    return Operator.NEQ;
        if (W_EQUALS.matcher(t).find())        return Operator.EQ;
        return null;
    }

    private static PatternKind detectPatternKind(String t, Quantifier q, boolean neg, Operator op) {
        // obligation-style phrasing ("must ...")
        boolean must = W_MUST.matcher(t).find();
        if (W_THERE_IS_NO.matcher(t).find()) return PatternKind.NONE_EXISTS;
        if (must && q == Quantifier.ALL)     return PatternKind.ALL_MUST_EQUAL;
        if (must && q == Quantifier.EXISTS)  return PatternKind.AT_LEAST_ONE_MUST_EQUAL;
        if (neg && must)                     return PatternKind.NONE_MUST_EQUAL;
        return PatternKind.EXISTS;
    }

    private static AnchorScope scopeFromAnchor(String anchorPath) {
        if (anchorPath == null) return AnchorScope.UNKNOWN;
        String a = anchorPath.toLowerCase(Locale.ROOT);
        if (a.contains("specialprocedures"))            return AnchorScope.SP;
        if (a.contains("additionalinformation"))        return AnchorScope.AI;
        if (a.contains("additionaldocuments.exemption"))return AnchorScope.AD_EXEMPTION;
        if (a.contains("additionaldocuments.type"))     return AnchorScope.AD;
        if (a.endsWith("goodsitem"))                    return AnchorScope.GI;
        return AnchorScope.GI; // default GoodsItem parent
    }

    // ----- Path detection -----
    private record PathParts(String anchorPath, FieldKey fieldKey) {}

    // Matches a canonical GoodsItem path with up to two trailing segments (e.g., .type.code)
    private static final Pattern P_CANONICAL_PATH =
            Pattern.compile("(GoodsItem(?:\\.[A-Za-z]+)+)");

    private static PathParts detectPathParts(String rawTemplate) {
        if (rawTemplate == null) return null;

        Matcher m = P_CANONICAL_PATH.matcher(rawTemplate);
        if (!m.find()) return null;

        String full = m.group(1); // e.g., GoodsItem.additionalDocuments.type.code  OR GoodsItem.specialProcedures.code

        // Split and classify
        String lower = full.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".specialprocedures.code")) {
            return new PathParts("GoodsItem.specialProcedures", FieldKey.SPECIAL_PROCEDURE_CODE);
        }
        if (lower.endsWith(".additionalinformation.code")) {
            return new PathParts("GoodsItem.additionalInformation", FieldKey.ADDITIONAL_INFORMATION_CODE);
        }
        if (lower.endsWith(".additionaldocuments.type.code")) {
            return new PathParts("GoodsItem.additionalDocuments.type", FieldKey.ADDITIONAL_DOCUMENT_TYPE_CODE);
        }
        if (lower.endsWith(".additionaldocuments.exemption.code")) {
            return new PathParts("GoodsItem.additionalDocuments.exemption", FieldKey.ADDITIONAL_DOCUMENT_EXEMPTION_CODE);
        }
        if (lower.endsWith(".requestedprocedurecode")) {
            return new PathParts("GoodsItem", FieldKey.REQUESTED_PROCEDURE);
        }
        if (lower.endsWith(".previousprocedurecode")) {
            return new PathParts("GoodsItem", FieldKey.PREVIOUS_PROCEDURE);
        }

        // Singular alias: GoodsItem.specialProcedure.code
        if (lower.endsWith(".specialprocedure.code")) {
            return new PathParts("GoodsItem.specialProcedures", FieldKey.SPECIAL_PROCEDURE_CODE);
        }

        // Fallback: take anchor up to last dot and mark UNKNOWN field
        int lastDot = full.lastIndexOf('.');
        String anchor = (lastDot > "GoodsItem".length()) ? full.substring(0, lastDot) : "GoodsItem";
        return new PathParts(anchor, FieldKey.UNKNOWN);
    }

    // Heuristic when canonical dot-path is missing but words imply the area (RP/PP etc.)
    private static PathParts heuristicPathFromWords(String t) {
        if (t.contains("requested procedure code")) {
            return new PathParts("GoodsItem", FieldKey.REQUESTED_PROCEDURE);
        }
        if (t.contains("previous procedure code")) {
            return new PathParts("GoodsItem", FieldKey.PREVIOUS_PROCEDURE);
        }
        if (t.contains("special procedure")) {
            return new PathParts("GoodsItem.specialProcedures", FieldKey.SPECIAL_PROCEDURE_CODE);
        }
        if (t.contains("additional information")) {
            return new PathParts("GoodsItem.additionalInformation", FieldKey.ADDITIONAL_INFORMATION_CODE);
        }
        if (t.contains("additional document") && t.contains("type")) {
            return new PathParts("GoodsItem.additionalDocuments.type", FieldKey.ADDITIONAL_DOCUMENT_TYPE_CODE);
        }
        if (t.contains("additional document") && t.contains("exemption")) {
            return new PathParts("GoodsItem.additionalDocuments.exemption", FieldKey.ADDITIONAL_DOCUMENT_EXEMPTION_CODE);
        }
        return new PathParts("GoodsItem", FieldKey.UNKNOWN);
    }

    // ----- word/phrase detectors (case-insensitive on normalised text) -----
    private static final Pattern W_ALL             = Pattern.compile("\\ball\\b");
    private static final Pattern W_THERE_IS_NO     = Pattern.compile("\\bthere\\s+is\\s+no\\b");
    private static final Pattern W_NO_MATCHING     = Pattern.compile("\\bno\\s+matching\\b");
    private static final Pattern W_MUST            = Pattern.compile("\\bmust\\b");
    private static final Pattern W_MUST_NOT        = Pattern.compile("\\bmust\\s+not\\b");
    private static final Pattern W_IS_ONE_OF       = Pattern.compile("\\bis\\s+one\\s+of\\b");
    private static final Pattern W_IS_NOT_ONE_OF   = Pattern.compile("\\bis\\s+not\\s+one\\s+of\\b");
    private static final Pattern W_IN              = Pattern.compile("\\bin\\b");
    private static final Pattern W_NOT_IN          = Pattern.compile("\\bnot\\s+in\\b");
    private static final Pattern W_EQUALS          = Pattern.compile("\\bequals\\b");
    private static final Pattern W_NOT_EQUALS      = Pattern.compile("\\bnot\\s+equals\\b");

    private static String normalise(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        t = t.replace('\u2013','-').replace('\u2014','-')
             .replace('“','"').replace('”','"').replace('’','\'');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }
}
