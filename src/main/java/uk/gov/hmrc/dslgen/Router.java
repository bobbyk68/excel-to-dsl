// If this lives inside DslrGen, remove the package line.
// Otherwise keep/adjust it to match your project.
package uk.gov.hmrc.dslgen.format;

import uk.gov.hmrc.dslgen.ir.RuleIR;
import uk.gov.hmrc.dslgen.router.Signature;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DSLR Formatter — applies wording rules only (logic/emitter unchanged).
 *
 * Rules:
 * 1) Parent macro (by anchor):
 *    GOODS_ITEM                      -> "Goods item exists"
 *    GI_ADDITIONAL_DOCUMENTS         -> "Goods item with additional document exists"
 *    (others fallback to older stems)
 *
 * 2) Dash-line prefix:
 *    - Value ops (EQ/NEX/IN/NIN):     prefix = "with"
 *    - Existential:
 *         EX  (exists)                -> "with <label> exists"
 *         NEX (not exists, no values) -> "without <label> exists"
 *
 * 3) Operator words (canonical):
 *    EQ  -> "equals";  NEX -> "not equals";  IN -> "in";  NIN -> "not in";  EX -> "exists"
 *    (Emitter already negates THEN; we just print what we get.)
 *
 * 4) Values:
 *    Single -> "\"VAL\""
 *    Lists  -> "\"A\",\"B\",\"C\""   (NO square brackets)
 *    Existential -> no values printed.
 *
 * 5) Label prettifier:
 *    - Drop root segment before first '.'
 *    - Split on '.'; de-camel each token; lowercase words
 *    - Preserve acronyms (AEO, EORI, VAT, UCR) in uppercase
 *    - Join with spaces
 */
public final class DslrFormatter {

    // ── Parent macro overrides (add more as you standardise wording) ────────────
    private static final Map<Signature.Anchor, String> ANCHOR_MACROS = Map.of(
            Signature.Anchor.GOODS_ITEM, "Goods item exists",
            Signature.Anchor.GI_ADDITIONAL_DOCUMENTS, "Goods item with additional document exists"
    );

    // Fallback stems used if not in ANCHOR_MACROS (keeps older wording for now)
    private static String fallbackStem(Signature.Anchor a) {
        return switch (a) {
            case GOODS_ITEM -> "There is a Goods Item";
            case GI_SPECIAL_PROCEDURES -> "Special procedure exists";
            case GI_ADDITIONAL_INFORMATION -> "Additional information exists";
            case GI_ADDITIONAL_DOCUMENTS -> "Additional document exists";
            case GI_DECLARED_DUTY_TAX_FEES -> "Declared duty/tax/fee exists";
            case GI_ORIGIN -> "Origin exists";
            case DECL_AUTH_HOLDER -> "There is an Authorization Holder";
            case CONSIGNMENT_VALUATION_ADJUSTMENTS -> "There exists a Valuation Adjustment";
            default -> "Unknown anchor exists";
        };
    }

    // Acronyms to preserve in uppercase when prettifying labels
    private static final Set<String> ACRONYMS = Set.of("AEO", "EORI", "VAT", "UCR");

    public String toDslr(RuleIR ir) {
        StringBuilder sb = new StringBuilder();
        sb.append("when\n");

        if (ir.sections.size() == 1) {
            var sec = ir.sections.get(0);
            sb.append("  ").append(parentStem(sec.anchor)).append("\n");
            for (var cl : sortClauses(sec.clauses)) {
                sb.append("    - ").append(renderClause(cl)).append("\n");
            }
        } else if (ir.sections.size() == 2) {
            var a = ir.sections.get(0);
            var b = ir.sections.get(1);

            sb.append("  ").append(parentStem(a.anchor)).append("\n");
            sb.append("    - ").append(renderClause(a.clauses.get(0))).append("\n\n");

            sb.append("  ").append("[and]").append("\n");
            sb.append("  ").append(parentStem(b.anchor)).append("\n");
            sb.append("    - ").append(renderClause(b.clauses.get(0))).append("\n");
        } else {
            throw new IllegalStateException("Expected 1 or 2 sections; got " + ir.sections.size());
        }

        sb.append("then\n");
        for (var eff : ir.thenEffects) {
            if (eff.type == RuleIR.ThenEffect.Type.ERROR_CODE) {
                for (String c : eff.values) {
                    sb.append("  Add error code \"").append(c).append("\"\n");
                }
            }
        }
        sb.append("end\n");
        return sb.toString();
    }

    // ── Clause rendering with the agreed wording rules ──────────────────────────

    private String renderClause(RuleIR.Clause c) {
        boolean isExistential = (c.op == Signature.OpFamily.EX) ||
                (c.op == Signature.OpFamily.NEX && (c.values == null || c.values.isEmpty()));

        String label = prettyLabel(c.path);

        if (isExistential) {
            String prefix = (c.op == Signature.OpFamily.EX) ? "with" : "without";
            return prefix + " " + label + " exists";
        }

        // Value comparisons: always "with"
        String opWord = operatorWord(c.op);
        String valueStr = renderValuesNoBrackets(c.values);
        return "with " + label + " " + opWord + " " + valueStr;
    }

    private String parentStem(Signature.Anchor a) {
        return ANCHOR_MACROS.getOrDefault(a, fallbackStem(a));
    }

    private List<RuleIR.Clause> sortClauses(List<RuleIR.Clause> clauses) {
        // Stable, readable ordering when two lines exist
        return clauses.stream()
                .sorted(Comparator
                        .comparing((RuleIR.Clause c) -> c.path)
                        .thenComparing(c -> c.op.name())
                        .thenComparing(c -> c.values == null || c.values.isEmpty() ? "" : c.values.get(0)))
                .collect(Collectors.toList());
    }

    private String operatorWord(Signature.OpFamily op) {
        return switch (op) {
            case EQ  -> "equals";
            case NEX -> "not equals";
            case IN  -> "in";
            case NIN -> "not in";
            case EX  -> "exists";
        };
    }

    private String renderValuesNoBrackets(List<String> vals) {
        if (vals == null || vals.isEmpty()) return "\"\"";
        if (vals.size() == 1) return quote(vals.get(0));
        return vals.stream().map(this::quote).collect(Collectors.joining(","));
    }

    private String quote(String s) { return "\"" + s + "\""; }

    // ── Label prettifier: drop root, split '.', de-camel, preserve acronyms ─────
    private String prettyLabel(String dottedPath) {
        if (dottedPath == null || dottedPath.isBlank()) return "";
        String p = dottedPath.trim();

        int firstDot = p.indexOf('.');
        String withoutRoot = (firstDot > 0 && firstDot + 1 < p.length())
                ? p.substring(firstDot + 1)
                : p;

        String[] tokens = withoutRoot.split("\\.");
        List<String> words = new ArrayList<>();
        for (String t : tokens) {
            if (t.isBlank()) continue;
            words.addAll(deCamelToWords(t));
        }

        // Lowercase by default, then uppercase known acronyms
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String up = w.toUpperCase(Locale.ROOT);
            if (ACRONYMS.contains(up)) {
                words.set(i, up);
            } else {
                words.set(i, w.toLowerCase(Locale.ROOT));
            }
        }

        // Drop trailing "value" if redundant
        if (!words.isEmpty()) {
            String last = words.get(words.size() - 1);
            if ("value".equalsIgnoreCase(last)) {
                words.remove(words.size() - 1);
            }
        }

        return String.join(" ", words);
    }

    private List<String> deCamelToWords(String token) {
        // Split "previousProcedure" -> ["previous","Procedure"], "statementCode" -> ["statement","Code"]
        String spaced = token
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2"); // handle ALLCAPS followed by Camel
        String[] parts = spaced.split("[_\\s]+");
        return Arrays.asList(parts);
    }
}
