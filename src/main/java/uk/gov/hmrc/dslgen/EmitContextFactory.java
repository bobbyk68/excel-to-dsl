package uk.gov.hmrc.dslgen;

import java.util.*;
import uk.gov.hmrc.dslgen.emit.adapter.AtomicHitAdapter;

public final class EmitContextFactory {
    private EmitContextFactory(){}

    public static EmitContext fromHits(AtomicHitAdapter.AtomicHit leftHit,
                                       AtomicHitAdapter.AtomicHit rightHit,
                                       AtomicHitAdapter.AtomicHit thenHit) {
        Condition left  = AtomicHitAdapter.toCondition(leftHit);
        Condition right = AtomicHitAdapter.toCondition(rightHit);

        List<Condition> conds = dedupe(List.of(left, right));
        String anchorKey      = deriveAnchorKey(conds);
        String thenTargetKey  = deriveThenTargetKey(thenHit, conds);

        return new EmitContext(anchorKey, conds, new ThenPart(thenTargetKey));
    }

    /* --- helpers (unchanged from earlier) --- */
    private static List<Condition> dedupe(List<Condition> in){
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Condition> out = new ArrayList<>();
        for (Condition c : in) {
            String k = (c.anchorKey() + "|" + c.fieldKey() + "|" + c.operator() + "|" + c.displayValue()).trim();
            if (seen.add(k)) out.add(c);
        }
        return out;
    }

    private static String deriveAnchorKey(List<Condition> conds) {
        if (conds.isEmpty()) return "Unknown";
        boolean allGoods = conds.stream().allMatch(c -> c.anchorKey()!=null && c.anchorKey().startsWith("GoodsItem"));
        if (allGoods) return "GoodsItem";
        String first = Optional.ofNullable(conds.get(0).anchorKey()).orElse("Unknown");
        int dot = first.indexOf('.');
        String head = dot >= 0 ? first.substring(0, dot) : first;
        boolean allShareHead = conds.stream().allMatch(c -> {
            String a = Optional.ofNullable(c.anchorKey()).orElse("");
            return a.equals(head) || a.startsWith(head + ".");
        });
        return allShareHead ? head : first;
    }

    private static String deriveThenTargetKey(AtomicHitAdapter.AtomicHit thenHit, List<Condition> conds) {
        if (thenHit != null) {
            String token = Optional.ofNullable(thenHit.getFieldToken()).orElse("").trim().toUpperCase();
            switch (token) {
                case "SP": case "SP_CODE": case "SPECIAL_PROCEDURE": return "specialProcedure";
                case "RP_PP": case "REQ_PREV":                        return "requestedAndPrevious";
                case "SP_AI":                                        return "spAndAi";
                case "SP_AD":                                        return "spAndAd";
                case "REQ_SP":                                       return "requestedAndSpecial";
            }
        }
        // fallback inference from IF side:
        boolean hasSP = conds.stream().anyMatch(c -> "specialProcedure.code".equals(c.fieldKey()));
        boolean hasRP = conds.stream().anyMatch(c -> "requestedProcedureCode".equals(c.fieldKey()));
        boolean hasPP = conds.stream().anyMatch(c -> "previousProcedureCode".equals(c.fieldKey()));
        boolean hasAI = conds.stream().anyMatch(c -> "additionalInformation.code".equals(c.fieldKey()));
        boolean hasAD = conds.stream().anyMatch(c -> "additionalDocuments.type.code".equals(c.fieldKey()));
        if (hasSP && hasAI) return "spAndAi";
        if (hasSP && hasAD) return "spAndAd";
        if (hasRP && hasPP) return "requestedAndPrevious";
        if (hasRP && hasSP) return "requestedAndSpecial";
        if (hasSP) return "specialProcedure";
        if (hasRP) return "requestedProcedure";
        if (hasPP) return "previousProcedure";
        return "validation";
    }
}
