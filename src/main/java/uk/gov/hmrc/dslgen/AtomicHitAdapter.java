package uk.gov.hmrc.dslgen;

import java.util.*;
import java.util.stream.Collectors;

import uk.gov.hmrc.dslgen.emit.Condition;
import uk.gov.hmrc.dslgen.emit.Operator;

/**
 * Adapts a parsed AtomicHit (your parser's output) into our canonical Condition.
 * Centralises token→canonical mapping, operator mapping, and value normalisation/quoting.
 */
public final class AtomicHitAdapter {

    private AtomicHitAdapter() {}

    /** Convert one AtomicHit into a canonical Condition. */
    public static Condition toCondition(AtomicHit hit) {
        if (hit == null) throw new IllegalArgumentException("hit is null");

        String anchorKey = mapAnchor(hit.getAnchorToken());     // e.g. "GoodsItem.specialProcedures"
        String fieldKey  = mapField(hit.getFieldToken(), anchorKey); // e.g. "specialProcedure.code"
        Operator op      = mapOperator(hit.getOperatorToken()); // e.g. EQUALS / IN / NOT_IN / EXISTS
        String value     = normaliseValue(op, hit.getRawValue());

        return new Condition(anchorKey, fieldKey, op, value);
    }

    /* -------------------- token mapping tables -------------------- */

    // Anchors: tokens from Excel → canonical anchor keys used by emitters
    private static final Map<String, String> ANCHOR_MAP = Map.ofEntries(
        Map.entry("SP",  "GoodsItem.specialProcedures"),
        Map.entry("AI",  "GoodsItem.additionalInformation"),
        Map.entry("AD",  "GoodsItem.additionalDocuments"),
        Map.entry("GI",  "GoodsItem"),                    // generic goods item row
        Map.entry("RP",  "GoodsItem"),                    // requested/previous proc often under GI
        Map.entry("PP",  "GoodsItem")
    );

    // Field tokens (often column-level shorthands) → canonical field keys
    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
        Map.entry("SP_CODE",            "specialProcedure.code"),
        Map.entry("SP_CATEGORY",        "specialProcedure.category"),
        Map.entry("SP_SEQUENCE",        "specialProcedure.sequence"),
        Map.entry("REQ_PROC",           "requestedProcedureCode"),
        Map.entry("PREV_PROC",          "previousProcedureCode"),
        Map.entry("AI_CODE",            "additionalInformation.code"),
        Map.entry("AD_TYPE_CODE",       "additionalDocuments.type.code")
        // add more as your Excel uses them
    );

    // Operator tokens → enum
    private static Operator mapOperator(String token) {
        if (token == null) return Operator.EQUALS;
        String t = token.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "=":
            case "equals":
            case "eq":      return Operator.EQUALS;
            case "in":      return Operator.IN;
            case "not in":
            case "nin":     return Operator.NOT_IN;
            case "exists":  return Operator.EXISTS;
            case "not exists":
            case "nexists": return Operator.NOT_EXISTS;
            case ">":       return Operator.GT;
            case ">=":      return Operator.GTE;
            case "<":       return Operator.LT;
            case "<=":      return Operator.LTE;
            default:        throw new IllegalArgumentException("Unknown operator token: " + token);
        }
    }

    private static String mapAnchor(String anchorToken) {
        if (anchorToken == null) return "GoodsItem";
        String key = ANCHOR_MAP.get(anchorToken.trim().toUpperCase(Locale.ROOT));
        return key != null ? key : "GoodsItem"; // safe fallback
    }

    private static String mapField(String fieldToken, String anchorKey) {
        if (fieldToken == null) {
            // Try sensible default from anchor
            if (anchorKey.endsWith(".specialProcedures")) return "specialProcedure.code";
            return "unknown";
        }
        String canonical = FIELD_MAP.get(fieldToken.trim().toUpperCase(Locale.ROOT));
        if (canonical != null) return canonical;

        // fallback: if the token looks like a dotted canonical already, pass it through
        if (fieldToken.contains(".")) return fieldToken;
        return fieldToken; // last-resort: echo
    }

    /* -------------------- value normalisation -------------------- */

    /**
     * Normalise the value string:
     * - For EXISTS/NOT_EXISTS: return empty string.
     * - For lists: return comma-separated items each quoted, e.g. "\"01\",\"02\"".
     * - For single literal: ensure wrapped in double quotes unless already quoted.
     */
    private static String normaliseValue(Operator op, String raw) {
        if (op == Operator.EXISTS || op == Operator.NOT_EXISTS) return "";
        if (raw == null || raw.trim().isEmpty()) return "\"\"";

        String s = raw.trim();

        // If already looks like a quoted CSV, accept as-is.
        if (s.contains(",") && (s.contains("\"") || s.contains("'"))) {
            return s;
        }

        // If contains comma but unquoted → split and quote each
        if (s.contains(",")) {
            List<String> parts = Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .map(AtomicHitAdapter::quoteIfNeeded)
                    .collect(Collectors.toList());
            return String.join(",", parts);
        }

        // Single value → ensure quoted
        return quoteIfNeeded(s);
    }

    private static String quoteIfNeeded(String v) {
        if (v == null || v.isBlank()) return "\"\"";
        String s = v.trim();
        boolean alreadyQuoted = (s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"));
        return alreadyQuoted ? s : "\"" + s + "\"";
    }

    /* -------------------- minimal AtomicHit shape -------------------- */

    /**
     * This is the minimal interface we expect from your parser.
     * If you already have a class with different method names, either:
     *  - make it implement this interface, or
     *  - write a small shim that reads from your existing getters.
     */
    public interface AtomicHit {
        String getAnchorToken();   // e.g. "SP", "AI", "AD", "GI", "RP", "PP"
        String getFieldToken();    // e.g. "SP_CODE", "REQ_PROC"
        String getOperatorToken(); // e.g. "=", "in", "not in", "exists"
        String getRawValue();      // e.g. B02 OR 01,02 OR "C501","C502"
    }
}
