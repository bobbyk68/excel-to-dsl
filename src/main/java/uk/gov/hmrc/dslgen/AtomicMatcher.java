// ============================================================================
// PACKAGE: uk.gov.hmrc.dslgen.emit
// Single-file drop-in containing:
//   1) AtomicMatcher  – value-only JSON, literal-first path/op, 2nd-IF absence handling
//   2) PathMapper     – canonical path → tokens (SP/AI/AD/RP/PP)
//   3) AtomicHitAdapter normaliseValue(...) PATCH (as a comment block to paste into your adapter)
// ----------------------------------------------------------------------------
// Notes:
// - Keep this file as AtomicMatcher.java (only one public class). PathMapper is package-private.
// - AtomicMatcher.AtomicHit implements your existing AtomicHitAdapter.AtomicHit interface.
// - You STILL need to paste the normaliseValue(...) patch into your own AtomicHitAdapter class.
// ============================================================================

package uk.gov.hmrc.dslgen;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AtomicMatcher {

    /** Your compiled JSON entry: just { pattern (regex), dsl (phrase) }. */
    public static final class CompiledEntry {
        private final Pattern regex;
        private final String dsl;
        public CompiledEntry(Pattern regex, String dsl) {
            this.regex = Objects.requireNonNull(regex);
            this.dsl = Objects.requireNonNull(dsl);
        }
        public Pattern regex(){ return regex; }
        public String  dsl(){   return dsl;   }
    }

    private final List<CompiledEntry> entries;

    public AtomicMatcher(List<CompiledEntry> entries) {
        this.entries = Objects.requireNonNull(entries);
    }

    /** First IF (left column in Excel). */
    public AtomicHit matchIf(String literal)  { return match(literal, /*isSecondIf*/ false); }

    /** Second IF (your sheet’s “thenCondition” column). */
    public AtomicHit matchThen(String literal){ return match(literal, /*isSecondIf*/ true);  }

    private AtomicHit match(String literal, boolean isSecondIf) {
        if (literal == null || literal.isBlank())
            throw new IllegalArgumentException("Empty clause");
        final String norm = normalise(literal);

        for (CompiledEntry ce : entries) {
            Matcher m = ce.regex().matcher(norm);

            // Support full-line AND tail patterns (e.g., "must equals (.+)")
            if (!(m.matches() || m.find())) continue;

            // JSON captures only the VALUE as group(1)
            if (m.groupCount() < 1 || m.group(1) == null || m.group(1).trim().isEmpty())
                throw new IllegalStateException("Pattern matched but no value captured: " + ce.regex());

            final String value = m.group(1).trim();

            // Derive PATH and default bullet-operator from the literal (not from DSL)
            final String path          = deriveCanonicalPathFromLiteral(norm);      // e.g., GoodsItem.additionalInformation.code
            final String bulletOpToken = parseBulletOperatorFromLiteral(norm);      // "equals" | "in" | "not in"

            // Decide final operator for the hit (second IF only can flip)
            final String operatorToken = decideRightOperatorToken(norm, bulletOpToken, isSecondIf);

            // Map canonical path → tokens and build the hit (KEEP the value)
            final PathMapper.Tokens tk = PathMapper.tokensFor(path);

            return new AtomicHit()
                    .setAnchorToken(tk.anchorTok)
                    .setFieldToken(tk.fieldTok)
                    .setOperatorToken(operatorToken)
                    .setRawValue(value);
        }

        throw new IllegalStateException("No JSON pattern matched: " + literal);
    }

    // ---------------- decision helpers ----------------

    /**
     * For the SECOND IF ONLY:
     *  - "must equals X" / "there is no ..." → NOT_EXISTS  (existence flips: "No matching ... exists")
     *  - "all ... must be/is one of S"       → NOT_IN      (existence stays "Matching ... exists")
     * Otherwise keep the bullet operator ("equals"/"in"/"not in").
     */
    private static String decideRightOperatorToken(String norm, String bulletOpToken, boolean isSecondIf) {
        if (!isSecondIf) return bulletOpToken;

        final String t = norm.toLowerCase(Locale.ROOT);

        // (A) All-items membership constraint → violation is "exists value outside S"
        final boolean isAll = t.startsWith("all ") || t.contains(" all ");
        final boolean saysOneOf = t.contains("is one of") || t.contains(" in ");
        final boolean hasMust   = t.contains(" must ");
        if (isAll && hasMust && saysOneOf) {
            return "not in"; // RP–PP style: keep existence, invert comparison to NOT IN
        }

        // (B) Required-but-missing (absence)
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

    /** Pull a canonical model path out of the literal. Never guess blindly. */
    private static String deriveCanonicalPathFromLiteral(String norm) {
        // 1) Try explicit dot-path in the literal
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
        // 2) Fallback to word cues
        String t = norm.toLowerCase(Locale.ROOT);
        if (t.contains("special procedure"))              return "GoodsItem.specialProcedures.code";
        if (t.contains("additional information"))         return "GoodsItem.additionalInformation.code";
        if (t.contains("additional document") && t.contains("type"))
            return "GoodsItem.additionalDocuments.type.code";
        if (t.contains("additional document") && t.contains("exemption"))
            return "GoodsItem.additionalDocuments.exemption.code";
        if (t.contains("requested procedure code"))       return "GoodsItem.requestedProcedureCode";
        if (t.contains("previous procedure code"))        return "GoodsItem.previousProcedureCode";

        // 3) DO NOT GUESS — fail fast so we add a pattern rather than mis-route
        throw new IllegalStateException("Cannot resolve path from literal: " + norm);
    }

    /** Extract bullet operator ("equals" | "in" | "not in") from the literal. */
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

    // ---- Your concrete AtomicHit; implements your existing adapter interface ----
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
}

/* ========================================================================== */
/* PACKAGE-PRIVATE: PathMapper – canonical path → tokens                       */
/* ========================================================================== */
final class PathMapper {

    static final class Tokens {
        final String anchorTok, fieldTok;
        Tokens(String a, String f){ this.anchorTok=a; this.fieldTok=f; }
    }

    static PathMapper.Tokens tokensFor(String pathRaw){
        if (pathRaw == null) return new Tokens("GI","UNKNOWN");
        String p = pathRaw.trim().toLowerCase(Locale.ROOT);

        // SP / AI / AD families
        if (p.equals("goodsitem.specialprocedures.code"))            return new Tokens("SP","SP_CODE");
        if (p.equals("goodsitem.additionalinformation.code"))        return new Tokens("AI","AI_CODE");
        if (p.equals("goodsitem.additionaldocuments.type.code"))     return new Tokens("AD","AD_TYPE_CODE");
        if (p.equals("goodsitem.additionaldocuments.exemption.code"))return new Tokens("AD","AD_EXEMPT_CODE");

        // RP / PP
        if (p.equals("goodsitem.requestedprocedurecode"))            return new Tokens("GI","REQ_PROC");
        if (p.equals("goodsitem.previousprocedurecode"))             return new Tokens("GI","PREV_PROC");

        // Last resort: treat as unknown field on GoodsItem
        return new Tokens("GI", pathRaw);
    }
}

/* ========================================================================== */
/* PATCH to apply inside your existing AtomicHitAdapter class                  */
/* Replace ONLY the normaliseValue(...) with the version below.               */
/* Keeps values for NOT_EXISTS and quotes properly.                            */
/* ========================================================================== */
/*
private static String normaliseValue(Operator op, String raw){
    if (op == Operator.EXISTS) return "";              // pure presence hides value
    if (raw == null || raw.isBlank()) return "\"\"";
    String s = raw.trim();

    // preserve quoted lists or quoted singletons
    if (s.contains(",") && (s.startsWith("\"") || s.startsWith("'"))) return s;

    // list without quotes -> quote each token
    if (s.contains(",")) {
        String[] parts = s.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = quote(parts[i].trim());
        return String.join(",", parts);
    }

    // single value -> ensure quoted
    return quote(s);
}
private static String quote(String v) {
    if (v == null || v.isBlank()) return "\"\"";
    String s = v.trim();
    boolean alreadyQuoted = (s.startsWith("\"") && s.endsWith("\"")) ||
                            (s.startsWith("'")  && s.endsWith("'"));
    return alreadyQuoted ? s : "\"" + s + "\"";
}
*/
