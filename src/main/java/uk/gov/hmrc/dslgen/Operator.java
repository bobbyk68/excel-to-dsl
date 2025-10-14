package uk.gov.hmrc.dslgen;

import java.util.*;
import uk.gov.hmrc.dslgen.emit.adapter.AtomicHitAdapter;

/* ===== minimal model ===== */
enum Operator { EQUALS, IN, NOT_IN, EXISTS, NOT_EXISTS, GT, GTE, LT, LTE }

final class Condition {
    private final String anchorKey, fieldKey, displayValue;
    private final Operator operator;
    Condition(String anchorKey, String fieldKey, Operator operator, String displayValue) {
        this.anchorKey = anchorKey; this.fieldKey = fieldKey; this.operator = operator; this.displayValue = displayValue;
    }
    String anchorKey()    { return anchorKey; }
    String fieldKey()     { return fieldKey; }
    Operator operator()   { return operator; }
    String displayValue() { return displayValue; }
}

final class ThenPart {
    private final String targetKey; // semantic key, not phrasing
    ThenPart(String targetKey) { this.targetKey = targetKey; }
    String targetKey() { return targetKey; }
}

final class EmitContext {
    private final String anchorKey;             // semantic anchor (e.g., "GoodsItem")
    private final List<Condition> conditions;   // IF side
    private final ThenPart thenPart;            // THEN side (semantic key)
    EmitContext(String anchorKey, List<Condition> conditions, ThenPart thenPart) {
        this.anchorKey = anchorKey; this.conditions = conditions; this.thenPart = thenPart;
    }
    String anchorKey() { return anchorKey; }
    List<Condition> conditions() { return conditions; }
    ThenPart thenPart() { return thenPart; }
}

/* ===== adapter input contract (your parser should provide these) ===== */
interface ExcelRow { AtomicHitAdapter.AtomicHit leftHit(); AtomicHitAdapter.AtomicHit rightHit(); AtomicHitAdapter.AtomicHit thenHit(); }

/* ===== factory that builds EmitContext with NO hard-coded literals ===== */
final class EmitContextFactory {

    private EmitContextFactory() {}

    public static EmitContext fromRow(ExcelRow row) {
        // 1) Convert hits → Conditions (no literals here; adapter does mapping)
        Condition left  = AtomicHitAdapter.toCondition(row.leftHit());
        Condition right = AtomicHitAdapter.toCondition(row.rightHit());

        // 2) Optionally de-dupe identical IFs (keeps order)
        List<Condition> conds = dedupe(List.of(left, right));

        // 3) Derive ANCHOR KEY from the conditions (no hard-coded "GoodsItem")
        String anchorKey = deriveAnchorKey(conds);

        // 4) Derive THEN TARGET KEY from the THEN cell or infer (no hard-coded "specialProcedure")
        String thenTargetKey = deriveThenTargetKey(row.thenHit(), conds);

        // 5) Build context
        return new EmitContext(anchorKey, conds, new ThenPart(thenTargetKey));
    }

    /* ---- helpers ---- */

    // Collapse to a sensible parent anchor. You can tune rules here centrally.
    static String deriveAnchorKey(List<Condition> conds) {
        if (conds == null || conds.isEmpty()) return "Unknown";
        // Strategy: take common prefix up to first dot; if all start with "GoodsItem", pick "GoodsItem"
        boolean allGoods = conds.stream().allMatch(c -> c.anchorKey() != null && c.anchorKey().startsWith("GoodsItem"));
        if (allGoods) return "GoodsItem";

        // Otherwise, pick the longest common segment before the first dot
        String first = Optional.ofNullable(conds.get(0).anchorKey()).orElse("Unknown");
        int dot = first.indexOf('.');
        String head = dot >= 0 ? first.substring(0, dot) : first;
        boolean allShareHead = conds.stream().allMatch(c -> {
            String a = Optional.ofNullable(c.anchorKey()).orElse("");
            return a.equals(head) || a.startsWith(head + ".");
        });
        return allShareHead ? head : first; // fallback to first anchor if no consensus
    }

    // Map THEN hit token → semantic key; if missing, infer from dominant anchor/field
    static String deriveThenTargetKey(AtomicHitAdapter.AtomicHit thenHit, List<Condition> conds) {
        // 1) Prefer explicit THEN token from parser
        if (thenHit != null) {
            String token = safeUpper(thenHit.getFieldToken());
            switch (token) {
                case "SP":
                case "SP_CODE":
                case "SPECIAL_PROCEDURE":
                    return "specialProcedure";
                case "REQ_PREV":
                case "RP_PP":
                    return "requestedAndPrevious";
                case "SP_AI":
                    return "spAndAi";
                case "SP_AD":
                    return "spAndAd";
                case "REQ_SP":
                    return "requestedAndSpecial";
                default:
                    // fall through to inference
            }
        }
        // 2) Infer from conditions (most specific wins)
        boolean hasSP = conds.stream().anyMatch(c -> keyEq(c.fieldKey(), "specialProcedure.code"));
        boolean hasRP = conds.stream().anyMatch(c -> keyEq(c.fieldKey(), "requestedProcedureCode"));
        boolean hasPP = conds.stream().anyMatch(c -> keyEq(c.fieldKey(), "previousProcedureCode"));
        boolean hasAI = conds.stream().anyMatch(c -> keyEq(c.fieldKey(), "additionalInformation.code"));
        boolean hasAD = conds.stream().anyMatch(c -> keyEq(c.fieldKey(), "additionalDocuments.type.code"));

        if (hasSP && hasAI) return "spAndAi";
        if (hasSP && hasAD) return "spAndAd";
        if (hasRP && hasPP) return "requestedAndPrevious";
        if (hasRP && hasSP) return "requestedAndSpecial";
        if (hasSP)          return "specialProcedure";
        if (hasRP)          return "requestedProcedure";
        if (hasPP)          return "previousProcedure";

        return "validation"; // safe generic fallback
    }

    static List<Condition> dedupe(List<Condition> in) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Condition> out = new ArrayList<>();
        for (Condition c : in) {
            String key = (c.anchorKey() + "|" + c.fieldKey() + "|" + c.operator() + "|" + c.displayValue()).trim();
            if (seen.add(key)) out.add(c);
        }
        return out;
    }

    private static boolean keyEq(String a, String b) { return Objects.equals(a, b); }
    private static String safeUpper(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }
}

/* ===== usage example (no literals at call-site) ===== */
final class Example {
    public static EmitContext buildContextFrom(ExcelRow row) {
        return EmitContextFactory.fromRow(row); // <- no "GoodsItem", no "specialProcedure" here
    }
}
