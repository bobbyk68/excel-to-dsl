package uk.gov.hmrc.dslgen.emit;

import java.util.*;

/**
 * Shaper: turns two IF-side AtomicHits (PRIMARY, MATCHING) into an EmitContext.
 * - No promotion from THEN (your thenCondition is already the 2nd IF clause).
 * - Absence ("No matching ... exists") must already be encoded by the matcher
 *   as operatorToken = "not exists" on the right-hand hit.
 */
public final class Shaper {

    private Shaper() {}

    /**
     * Preferred entry: shape the pair of IF hits (left = PRIMARY, right = MATCHING).
     * @param leftHit  first IF clause (from row.ifCondition)
     * @param rightHit second IF clause (from row.thenCondition) — may be null
     */
    public static EmitContext shape(AtomicHitAdapter.AtomicHit leftHit,
                                    AtomicHitAdapter.AtomicHit rightHit) {

        if (leftHit == null) throw new IllegalArgumentException("leftHit (PRIMARY) is required");

        // Map to canonical Conditions
        Condition left  = AtomicHitAdapter.toCondition(leftHit);
        Condition right = (rightHit != null) ? AtomicHitAdapter.toCondition(rightHit) : null;

        // Keep order: left then right (if present)
        List<Condition> conds = new ArrayList<>(2);
        conds.add(left);
        if (right != null) conds.add(right);

        // De-duplicate identical conditions (anchor|field|op|value)
        conds = dedupeBySemanticKey(conds);

        // Bind parent anchor (GoodsItem for your current space)
        String parentAnchor = deriveParentAnchor(conds);

        // Infer a simple then-target key from the pair composition
        String thenKey = deriveThenTargetKey(conds);

        return new EmitContext(parentAnchor, conds, new ThenPart(thenKey));
    }

    /**
     * Backward-compatible overload: if callers still send a third arg (historical THEN),
     * we ignore it and log (because in this pipeline THEN is the emit line, not a condition).
     */
    public static EmitContext shape(AtomicHitAdapter.AtomicHit leftHit,
                                    AtomicHitAdapter.AtomicHit rightHit,
                                    AtomicHitAdapter.AtomicHit ignoredThenHit) {
        // (Optional) debug: System.out.println("[Shaper] ignoring third arg thenHit; pair comes from IF+thenCondition");
        return shape(leftHit, rightHit);
    }

    // ---------------------- helpers ----------------------

    private static List<Condition> dedupeBySemanticKey(List<Condition> in) {
        if (in == null || in.isEmpty()) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Condition> out = new ArrayList<>(in.size());
        for (Condition c : in) {
            String key = semanticKey(c);
            if (seen.add(key)) out.add(c);
        }
        return out;
    }

    private static String semanticKey(Condition c) {
        // If your Condition already has c.semanticKey(), prefer that.
        try {
            var m = c.getClass().getMethod("semanticKey");
            Object v = m.invoke(c);
            if (v != null) return v.toString();
        } catch (Exception ignored) {}
        // Fallback: compose our own
        String a = c.anchorKey()    == null ? "" : c.anchorKey();
        String f = c.fieldKey()     == null ? "" : c.fieldKey();
        String o = c.operator()     == null ? "?" : c.operator().name();
        String v = c.displayValue() == null ? "" : c.displayValue().trim();
        return a + "|" + f + "|" + o + "|" + v;
    }

    private static String deriveParentAnchor(List<Condition> conds) {
        if (conds == null || conds.isEmpty()) return "Unknown";
        boolean allGoods = conds.stream()
                .map(Condition::anchorKey)
                .filter(Objects::nonNull)
                .allMatch(a -> a.startsWith("GoodsItem"));
        if (allGoods) return "GoodsItem";
        String first = Optional.ofNullable(conds.get(0).anchorKey()).orElse("Unknown");
        int dot = first.indexOf('.');
        return dot >= 0 ? first.substring(0, dot) : first;
    }

    private static String deriveThenTargetKey(List<Condition> conds) {
        // Infer from the IF pair only (no reliance on a historical THEN hit)
        boolean hasSP = hasField(conds, "specialProcedure.code");
        boolean hasAI = hasField(conds, "additionalInformation.code");
        boolean hasAD = hasField(conds, "additionalDocuments.type.code");
        boolean hasRP = hasField(conds, "requestedProcedureCode");
        boolean hasPP = hasField(conds, "previousProcedureCode");

        if (hasSP && hasAI) return "spAndAi";
        if (hasSP && hasAD) return "spAndAd";
        if (hasRP && hasPP) return "requestedAndPrevious";
        if (hasSP)          return "specialProcedure";
        return "validation";
    }

    private static boolean hasField(List<Condition> conds, String fieldKey) {
        return conds.stream().anyMatch(c -> fieldKey.equals(c.fieldKey()));
    }
}
