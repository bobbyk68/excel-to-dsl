package uk.gov.hmrc.dslgen.emit;

import uk.gov.hmrc.dslgen.pattern.PatternIntrospector;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AtomicMatcher that works with your existing Runner:
 *   List<AtomicMatcher.CompiledAtomic> compiled = ...;
 *   AtomicMatcher m = new AtomicMatcher(compiled);
 *
 * JSON/DSL contract: pattern captures only the VALUE (group 1); DSL is a phrase template.
 * We derive PATH + bullet OP from the Excel literal; for the 2nd IF we detect absence or "all ... must be one of".
 */
public final class AtomicMatcher {

    // ======================================================================
    // Legacy-expected compiled type (kept exactly for your Runner)
    // ======================================================================
    public static final class CompiledAtomic {
        private final String id;
        private final Pattern regex;
        private final String pattern; // original regex source (for logging)
        private final String dsl;     // phrase template (kept for reference)
        private final PatternIntrospector.Meta meta; // parsed from DSL (debug-only)

        private CompiledAtomic(String id, Pattern regex, String pattern, String dsl, PatternIntrospector.Meta meta) {
            this.id = id;
            this.regex = regex;
            this.pattern = pattern;
            this.dsl = dsl;
            this.meta = meta;
        }

        /** Factory used by your Runner: CompiledAtomic.compile(id, regexSource, pattern, dsl). */
        public static CompiledAtomic compile(String id, String regexSource, String pattern, String dsl) {
            Pattern rx = Pattern.compile(regexSource, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            PatternIntrospector.Meta meta = PatternIntrospector.parse(dsl);
            if (meta == null) {
                // Safe defaults (not used for structure now—only for debugging)
                meta = new PatternIntrospector.Meta(
                        PatternIntrospector.AnchorScope.GI,
                        "GoodsItem",
                        PatternIntrospector.FieldKey.UNKNOWN,
                        PatternIntrospector.Operator.EQ,
                        PatternIntrospector.Quantifier.EXISTS,
                        false,
                        PatternIntrospector.PatternKind.UNKNOWN
                );
            }
            return new CompiledAtomic(id, rx, pattern, dsl, meta);
        }

        public String id() { return id; }
        public Pattern regex() { return regex; }
        public String pattern() { return pattern; }
        public String dsl() { return dsl; }
        public PatternIntrospector.Meta meta() { return meta; }
    }

    // ======================================================================
    // Matcher state & ctor
    // ======================================================================
    private final List<CompiledAtomic> compiled;

    public AtomicMatcher(List<CompiledAtomic> compiled) {
        this.compiled = Objects.requireNonNull(compiled);
    }

    /** First IF (left column). */
    public AtomicHit matchIf(String literal)  { return match(literal, /*isSecondIf*/ false); }

    /** Second IF (your sheet’s “thenCondition” column). */
    public AtomicHit matchThen(String literal){ return match(literal, /*isSecondIf*/ true);  }

    // ======================================================================
    // Core matching logic
    // ======================================================================
    private AtomicHit match(String literal, boolean isSecondIf) {
        if (literal == null || literal.isBlank())
            throw new IllegalArgumentException("Empty clause");
        final String norm = normalise(literal);

        for (CompiledAtomic ca : compiled) {
            Matcher m = ca.regex().matcher(norm);

            // Support both whole-line and tail patterns (e.g., "must equals (.+)")
            if (!(m.matches() || m.find())) continue;

            // Your JSON pattern captures only VALUE as group(1)
            if (m.groupCount() < 1 || m.group(1) == null || m.group(1).trim().isEmpty())
                throw new IllegalStateException("Pattern matched but no value captured: " + ca.pattern());

            final String value = m.group(1).trim();

            // Derive PATH + bullet operator from the literal (not from DSL/meta)
            final String path          = deriveCanonicalPathFromLiteral(norm); // e.g., GoodsItem.additionalInformation.code
            final String bulletOpToken = parseBulletOperatorFromLiteral(norm); // "equals" | "in" | "not in"

            // Decide final operator for the hit (2nd IF only may flip)
            final String operatorToken = decideRightOperatorToken(norm, bulletOpToken, isSecondIf);

            // Map canonical path → tokens and build the hit (KEEP value)
            final PathMapper.Tokens tk = PathMapper.tokensFor(path);

            return new AtomicHit()
                    .setAnchorToken(tk.anchorTok)
                    .setFieldToken(tk.fieldTok)
                    .setOperatorToken(operatorToken)
                    .setRawValue(value);
        }

        throw new IllegalStateException("No JSON pattern matched: " + literal);
    }

    /**
     * For the SECOND IF ONLY:
     *  - "all ... must be/is one of S" → NOT_IN (existence stays "Matching ... exists")
     *  - "must equal/equals", "there is no", "no matching" → NOT_EXISTS (existence flips)
     * Otherwise keep the bullet operator ("equals"/"in"/"not in").
     */
    private static String decideRightOperatorToken(String norm, String bulletOpToken, boolean isSecondIf) {
        if (!isSecondIf) return bulletOpToken;
        final String t = norm.toLowerCase(Locale.ROOT);

        // RP–PP style: "all ... must be/is one of S" ⇒ violation is "exists value outside S" ⇒ NOT_IN
        final boolean isAll     = t.startsWith("all ") || t.contains(" all ");
        final boolean saysOneOf = t.contains("is one of") || t.contains(" in ");
        final boolean hasMust   = t.contains(" must ");
        if (isAll && hasMust && saysOneOf) return "not in";

        // Required-but-missing: "must equal/equals", "there is no", "no matching"
        final boolean absence =
                t.contains("there is no ") ||
                        t.contains(" no matching ") ||
                        t.contains(" must equal")  ||
                        t.contains(" must equals") ||
                        t.contains(" must be ")    ||
                        t.contains(" must in ")    ||
                        t.contains(" must is one of");

        return absence ? "not exists" : bulletOpToken;
    }

    /** Canonical path from literal. Never guess blindly. */
    private static String deriveCanonicalPathFromLiteral(String norm) {
        // 1) dot path
        Matcher p = Pattern.compile("(GoodsItem(?:\\.[A-Za-z]+)+)", Pattern.CASE_INSENSITIVE).matcher(norm);
        if (p.find()) {
            String full = p.group(1).toLowerCase(Locale.ROOT);
            if (full.endsWith(".specialprocedures.code") || full.endsWith(".specialprocedure.code"))
                return "GoodsItem.specialProcedures.code";
            if (full.endsWith(".additionalinformation.code"))
                return "GoodsItem.additionalInformation.code";
            if (full.endsWith(".additionaldocuments.type.code"))
                return "GoodsItem.additionalDocuments.type.code";
            if (full.endsWith(".additionaldocuments.exemption.code"))
                return "GoodsItem.additionalDocuments.exemption.code";
            if (full.endsWith(".requestedprocedurecode"))
                return "GoodsItem.requestedProcedureCode";
            if (full.endsWith(".previousprocedurecode"))
                return "GoodsItem.previousProcedureCode";
        }
        // 2) keyword cues
        String t = norm.toLowerCase(Locale.ROOT);
        if (t.contains("special procedure"))              return "GoodsItem.specialProcedures.code";
        if (t.contains("additional information"))         return "GoodsItem.additionalInformation.code";
        if (t.contains("additional document") && t.contains("type"))
            return "GoodsItem.additionalDocuments.type.code";
        if (t.contains("additional document") && t.contains("exemption"))
            return "GoodsItem.additionalDocuments.exemption.code";
        if (t.contains("requested procedure code"))       return "GoodsItem.requestedProcedureCode";
        if (t.contains("previous procedure code"))        return "GoodsItem.previousProcedureCode";

        throw new IllegalStateException("Cannot resolve path from literal: " + norm);
    }

    /** Bullet operator from literal. */
    private static String parseBulletOperatorFromLiteral(String norm) {
        String t = norm.toLowerCase(Locale.ROOT);
        if (t.contains("is not one of") || t.contains(" not in ")) return "not in";
        if (t.contains("is one of")     || t.contains(" in "))     return "in";
        return "equals";
    }

    private static String normalise(String s){
        return s.replace('\u2013','-').replace('\u2014','-')
                .replace('“','"').replace('”','"').replace('’','\'').trim();
    }

    // ======================================================================
    // Concrete hit type (implements your existing adapter interface)
    // ======================================================================
    public static final class AtomicHit implements AtomicHitAdapter.AtomicHit {
        private String aTok, fTok, oTok, raw;
        @Override public String getAnchorToken(){ return aTok == null ? "" : aTok; }
        @Override public String getFieldToken(){  return fTok == null ? "" : fTok; }
        @Override public String getOperatorToken(){ return oTok == null ? "" : oTok; }
        @Override public String getRawValue(){    return raw == null ? "" : raw; }
        public AtomicHit setAnchorToken(String v){ this.aTok = v; return this; }
        public AtomicHit setFieldToken(String v){  this.fTok = v; return this; }
        public AtomicHit setOperatorToken(String v){ this.oTok = v; return this; }
        public AtomicHit setRawValue(String v){    this.raw = v;  return this; }
    }

    // ======================================================================
    // Embedded PathMapper so this stays a single file
    // ======================================================================
    static final class PathMapper {
        static final class Tokens {
            final String anchorTok, fieldTok;
            Tokens(String a, String f){ this.anchorTok=a; this.fieldTok=f; }
        }
        static Tokens tokensFor(String pathRaw){
            if (pathRaw == null) return new Tokens("GI","UNKNOWN");
            String p = pathRaw.trim().toLowerCase(Locale.ROOT);

            // SP / AI / AD families
            if (p.equals("goodsitem.specialprocedures.code"))             return new Tokens("SP","SP_CODE");
            if (p.equals("goodsitem.additionalinformation.code"))         return new Tokens("AI","AI_CODE");
            if (p.equals("goodsitem.additionaldocuments.type.code"))      return new Tokens("AD","AD_TYPE_CODE");
            if (p.equals("goodsitem.additionaldocuments.exemption.code")) return new Tokens("AD","AD_EXEMPT_CODE");

            // RP / PP
            if (p.equals("goodsitem.requestedprocedurecode"))             return new Tokens("GI","REQ_PROC");
            if (p.equals("goodsitem.previousprocedurecode"))              return new Tokens("GI","PREV_PROC");

            // Last resort (kept permissive to avoid hard failures mid-POC)
            return new Tokens("GI", pathRaw);
        }
    }
}
