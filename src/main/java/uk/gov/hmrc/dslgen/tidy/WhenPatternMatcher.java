// src/main/java/uk/gov/hmrc/rules/matcher/WhenPatternMatcher.java
package uk.gov.hmrc.dslgen.tidy;

import uk.gov.hmrc.rules.compose.HyphenTwoPhaseComposer;
import uk.gov.hmrc.rules.ast.ConstraintCase;
import uk.gov.hmrc.rules.model.AtomicHitView;
import uk.gov.hmrc.rules.model.RowHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WhenPatternMatcher {

    private final HyphenTwoPhaseComposer twoPhase = new HyphenTwoPhaseComposer();

    /**
     * Existing contract preserved: returns a DSLR string.
     * Two passes: (1) extract hits, (2) compose DSL lines.
     */
    public String collectAll(List<RuleRow> rows) throws IOException {
        // ---------- PASS 1: extract ----------
        List<RowHits> perRow = new ArrayList<>(rows.size());

        for (RuleRow row : rows) {
            String leftLiteral  = row.ifCondition();     // raw text from Excel
            String rightLiteral = row.thenCondition();   // raw text from Excel

            AtomicHit leftHit, rightHit;
            try {
                leftHit = matcher.matchAtomic(leftLiteral);   // your existing API
            } catch (Exception e) {
                System.out.println("L: " + leftLiteral + " :: " + row.businessRuleId());
                continue; // or collect errors
            }
            try {
                rightHit = matcher.matchAtomic(rightLiteral);
            } catch (Exception e) {
                System.out.println("R: " + rightLiteral + " :: " + row.businessRuleId());
                continue;
            }

            // Adapt your AtomicHit (internal) → AtomicHitView (composer-facing)
            var hits = List.of(
                    new AtomicHitView(
                            Objects.requireNonNull(leftHit.dslTemplate()),
                            Objects.requireNonNull(leftHit.capturedValue()),
                            leftHit.dataPath()
                    ),
                    new AtomicHitView(
                            Objects.requireNonNull(rightHit.dslTemplate()),
                            Objects.requireNonNull(rightHit.capturedValue()),
                            rightHit.dataPath()
                    )
            );

            perRow.add(new RowHits(row.businessRuleId(), row.operatorToken(), hits));
        }

        // ---------- PASS 2: compose ----------
        StringBuilder out = new StringBuilder(8_192);
        for (RowHits rh : perRow) {
            ConstraintCase kase = resolveCase(rh.operatorToken());

            for (AtomicHitView hit : rh.hits()) {
                // *** The single call that handles both cases:
                //     - no '-'  → one line
                //     - with '-'→ two lines (existence + predicate)
                List<String> lines = twoPhase.composeLeaf(
                        kase,
                        hit.dslTemplate(),
                        hit.rightLiteral(),
                        hit.dataPath()
                );
                for (String line : lines) out.append(line).append('\n');
            }
            out.append('\n'); // blank line between rules (optional)
        }

        return out.toString();
    }

    // Map your row token (pseudocode/JSON column) to a case.
// Default to ALL_OF (logical AND).
    private ConstraintCase resolveCase(String token) {
        if (token == null) return ConstraintCase.ALL_OF;
        switch (token.trim().toUpperCase()) {
            case "ONE":
            case "ONLY_ONE":
            case "ONE_OF":       return ConstraintCase.ONE_OF;
            case "NONE":
            case "NOT":          return ConstraintCase.NONE;
            case "ANY":
            case "ANY_OF":       return ConstraintCase.ANY_OF;
            case "ALL":
            case "ALL_OF":       return ConstraintCase.ALL_OF;
            case "EXISTS":
            case "AT_LEAST_ONE": return ConstraintCase.EXISTS;  // pattern implies existence
            case "NONE_OF":      return ConstraintCase.NONE_OF;
            default:             return ConstraintCase.ALL_OF;
        }
    }

    // Safe getter for the captured value from AtomicHit.groups()
    private String firstGroupOrEmpty(AtomicHit hit) {
        List<String> g = hit.groups();
        return (g != null && !g.isEmpty()) ? g.get(0) : "";
    }

    private final uk.gov.hmrc.dslgen.tidy.HyphenTwoPhaseComposer twoPhaseComposer = new uk.gov.hmrc.dslgen.tidy.HyphenTwoPhaseComposer();

    public String collectAll(List<RuleRow> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        List<RowHits> rowsHits = new ArrayList<>(rows.size());

        // ---------- PASS 1: extract ----------
        for (RuleRow row : rows) {
            String leftLiteral  = row.ifCondition();
            String rightLiteral = row.thenCondition();

            AtomicHit left, right;
            try {
                left = matcher.matchAtomic(leftLiteral);
            } catch (Exception e) {
                System.out.println("L: " + leftLiteral + " :: " + row.businessRuleId());
                continue;
            }
            try {
                right = matcher.matchAtomic(rightLiteral);
            } catch (Exception e) {
                System.out.println("R: " + rightLiteral + " :: " + row.businessRuleId());
                continue;
            }

            // Keep your composite ordered pair “found gate”
            String pairKey = left.id() + "|" + right.id();
            if (!compositeIndex.contains(pairKey)) {
                System.out.println("Rule " + row.businessRuleId() + " - Composite not found for pair: " + pairKey);
                continue;
            }

            // Stash the pair for pass 2
            rowsHits.add(new RowHits(
                    row.businessRuleId(),
                    row.operatorToken(),     // your pseudocode/JSON “op/quantifier”
                    List.of(left, right)
            ));
        }

        // ---------- PASS 2: compose ----------
        for (RowHits rh : rowsHits) {
            uk.gov.hmrc.dslgen.tidy.ConstraintCase kase = resolveCase(rh.operatorToken);

            // If you have per-row annotations/headers, keep using your dslWriter here if needed
            // dslWriter.appendRule(out, row, left.dsl(), right.dsl()); // (example)

            for (AtomicHit hit : rh.hits) {
                // >>> THIS IS THE CALL <<<
                // Handles both: template without '-' → 1 line
                //               template with    '-' → 2 lines (existence + predicate)
                List<String> lines = twoPhaseComposer.composeLeaf(
                        kase,
                        hit.dsl(),                 // the DSL template (may contain " - ")
                        firstGroupOrEmpty(hit),    // captured value (groups()[0])
                        hit.literal()              // optional “dataPath”/context; use literal/original if you like
                );

                for (String line : lines) {
                    out.append(line).append('\n');
                }
            }

            out.append('\n'); // blank line between rules (optional)
        }

        return out.toString();
    }



}
