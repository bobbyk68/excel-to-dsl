package uk.gov.hmrc.dslgen;

// ======================
// class Signature
// ======================
class Signature {

    // --- [ADDED FIELD] cross-instance flag (same anchor, different child instance) ---
    private final boolean crossInstance;

    // --- [ADDED FIELD] whether THEN should be negated (default true; co-existence sets false) ---
    private final boolean negateThen;

    // existing fields...
    private final Anchor ifAnchor;
    private final Anchor thenAnchor;
    private final boolean mergeable;
    final Kind ifKind;
    final Kind thenKind;

    // --- [CHANGED CTOR] include crossInstance & negateThen ---
    Signature(Anchor ifAnchor, Anchor thenAnchor, boolean mergeable,
              Kind ifKind, Kind thenKind,
              boolean crossInstance, boolean negateThen) {
        this.ifAnchor = ifAnchor;
        this.thenAnchor = thenAnchor;
        this.mergeable = mergeable;
        this.ifKind = ifKind;
        this.thenKind = thenKind;
        this.crossInstance = crossInstance;
        this.negateThen = negateThen;
    }

    // --- [ADDED GETTERS] ---
    boolean crossInstance() { return crossInstance; }
    boolean negateThen()    { return negateThen; }

    // existing: enums Anchor, OpFamily, Kind {...}
}


// ======================
// class SignatureBuilder
// ======================
class SignatureBuilder {

    // existing helpers: resolveAnchor(path), normalizeOp(opToken), lastSegment(path) ...

    // --- [CHANGED METHOD] build(...) adds cross-instance detection & negateThen policy ---
    Signature build(String ruleId, ParsedClause ifC, ParsedClause thenC) {
        var ifAnchor  = resolveAnchor(ifC.path());
        var thenAnchor= resolveAnchor(thenC.path());

        var ifKind  = new Kind(normalizeOp(ifC.opToken()), ifC.values().size() > 1 ? Kind.Card.MANY : (ifC.values().isEmpty() ? Kind.Card.NONE : Kind.Card.ONE));
        var thenKind= new Kind(normalizeOp(thenC.opToken()), thenC.values().size() > 1 ? Kind.Card.MANY : (thenC.values().isEmpty() ? Kind.Card.NONE : Kind.Card.ONE));

        boolean mergeable = (ifAnchor == thenAnchor);

        // --- [NEW] cross-instance heuristic: same collection anchor AND same leaf field ---
        boolean crossInstance = false;
        if (ifAnchor == Anchor.GI_SPECIAL_PROCEDURES && thenAnchor == Anchor.GI_SPECIAL_PROCEDURES) {
            var leafIf   = lastSegment(ifC.path());
            var leafThen = lastSegment(thenC.path());
            if (leafIf.equals(leafThen)) {
                crossInstance = true;
                mergeable = false; // cannot merge two constraints that must apply to different child rows
            }
        }
        // NOTE: same pattern generalises to other collection anchors later (AD, AI) if needed.

        // --- [NEW] negateThen policy ---
        boolean negateThen = true; // default for implication A -> NOT B
        if (crossInstance) {
            // For cross-instance, decide by original THEN polarity:
            // If THEN op was positive (EQ/IN/EX) → co-existence → do NOT negate
            // If THEN op was negative (NEX/NIN/not-exists) → exclusion → keep negation
            switch (thenKind.op) {
                case EQ, IN, EX -> negateThen = false;
                default -> negateThen = true;
            }
        }

        return new Signature(ifAnchor, thenAnchor, mergeable, ifKind, thenKind, crossInstance, negateThen);
    }
}


// ======================
// class Router
// ======================
class Router {

    // --- [UNCHANGED LOGIC] but explicitly ALLOW cross-instance for collection anchors ---
    RouteResult route(Signature sig) {
        // existing guards (unknown anchor, sentinels, etc.)

        // DO NOT block crossInstance; it is valid for collection anchors like SP.
        // If you had a policy flag earlier, ensure it's not rejecting crossInstance.

        return RouteResult.success();
    }

    // existing: RouteResult, etc.
}


// ======================
// class GenericEmitter
// ======================
class GenericEmitter {

    // existing: EmitContext, emit(...), op flip map, etc.

    // --- [CHANGED METHOD] emit(...) respects sig.negateThen() ---
    RuleIR emit(Signature sig, EmitContext ctx) {
        // Build Section A (IF) as before using ctx.ifClause

        // For THEN, apply conditional negation:
        var thenOp = ctx.thenClause.op();
        if (sig.negateThen()) {
            thenOp = switch (thenOp) {
                case EQ  -> Signature.OpFamily.NEX;
                case IN  -> Signature.OpFamily.NIN;
                case EX  -> Signature.OpFamily.NEX; // "not exists"
                case NEX -> Signature.OpFamily.EQ;
                case NIN -> Signature.OpFamily.IN;
            };
        }
        var thenClauseAdjusted = new Clause(ctx.thenClause.path(), thenOp, ctx.thenClause.card(), ctx.thenClause.values());

        // Shape: mergeable → 1 section; otherwise 2 sections
        RuleIR ir = new RuleIR();
        if (sig.mergeable) {
            ir.addSection(sig.ifAnchor, List.of(
                new Clause(ctx.ifClause.path(), ctx.ifClause.op(), ctx.ifClause.card(), ctx.ifClause.values()),
                thenClauseAdjusted
            ));
        } else {
            ir.addSection(sig.ifAnchor, List.of(
                new Clause(ctx.ifClause.path(), ctx.ifClause.op(), ctx.ifClause.card(), ctx.ifClause.values())
            ));
            ir.addSection(sig.thenAnchor, List.of(thenClauseAdjusted));
        }

        // --- [NEW FLAG] surface cross-instance so formatter can change parent wording for section 2 ---
        ir.setCrossInstance(sig.crossInstance());

        return ir;
    }
}


// ======================
// class RuleIR
// ======================
class RuleIR {

    static final class Section {
        final Signature.Anchor anchor;
        final java.util.List<Clause> clauses;
        Section(Signature.Anchor a, java.util.List<Clause> cs) { this.anchor = a; this.clauses = cs; }
    }

    static final class Clause {
        final String path;
        final Signature.OpFamily op;
        final Kind.Card card;
        final java.util.List<String> values;
        Clause(String path, Signature.OpFamily op, Kind.Card card, java.util.List<String> values) {
            this.path = path; this.op = op; this.card = card; this.values = values;
        }
    }

    // existing: ThenEffect list, sections list, addSection(...)

    // --- [ADDED FIELD] cross-instance signal for formatter ---
    private boolean crossInstance;

    // --- [ADDED MUTATOR/ACCESSOR] ---
    void setCrossInstance(boolean v) { this.crossInstance = v; }
    boolean isCrossInstance() { return crossInstance; }
}


// ======================
// class DslrFormatter
// ======================
class DslrFormatter {

    // existing: operator words, label prettifier, parent stems, etc.

    // --- [CHANGED METHOD] toDslr(...) tweaks section-2 parent wording for cross-instance SP-SP ---
    public String toDslr(RuleIR ir) {
        StringBuilder sb = new StringBuilder();
        sb.append("when\n");

        if (ir.sections.size() == 1) {
            var s = ir.sections.get(0);
            sb.append("  ").append(parentStem(s.anchor, /*isMatching=*/false, /*crossInstance=*/false)).append("\n");
            for (var c : s.clauses) sb.append("    - ").append(renderClause(c)).append("\n");

        } else if (ir.sections.size() == 2) {
            var s1 = ir.sections.get(0);
            var s2 = ir.sections.get(1);

            // Section 1: normal parent
            sb.append("  ").append(parentStem(s1.anchor, /*isMatching=*/false, ir.isCrossInstance())).append("\n");
            sb.append("    - ").append(renderClause(s1.clauses.get(0))).append("\n\n");

            // Section 2: if cross-instance and same SP anchor, use "Matching ..." wording
            boolean matching = ir.isCrossInstance() && s1.anchor == Signature.Anchor.GI_SPECIAL_PROCEDURES && s2.anchor == Signature.Anchor.GI_SPECIAL_PROCEDURES;

            sb.append("  ").append(matching ? "Matching " + baseStem(s2.anchor) : parentStem(s2.anchor, /*isMatching=*/false, ir.isCrossInstance())).append("\n");
            sb.append("    - ").append(renderClause(s2.clauses.get(0))).append("\n");

        } else {
            throw new IllegalStateException("Expected 1 or 2 sections; got " + ir.sections.size());
        }

        sb.append("then\n");
        // existing: print error codes / effects...
        sb.append("end\n");
        return sb.toString();
    }

    // --- [ADDED HELPER] parentStem with cross-instance awareness ---
    private String parentStem(Signature.Anchor a, boolean isMatching, boolean crossInstance) {
        if (isMatching) return "Matching " + baseStem(a);
        return baseStem(a);
    }

    // --- [ADDED HELPER] baseStem(...) returns the default parent text for an anchor ---
    private String baseStem(Signature.Anchor a) {
        return switch (a) {
            case GOODS_ITEM -> "Goods item exists";
            case GI_SPECIAL_PROCEDURES -> "Goods item with special procedure exists";
            case GI_ADDITIONAL_DOCUMENTS -> "Goods item with additional document exists";
            case GI_ADDITIONAL_INFORMATION -> "Additional information exists";
            case GI_DECLARED_DUTY_TAX_FEES -> "Declared duty/tax/fee exists";
            case GI_ORIGIN -> "Origin exists";
            case DECL_AUTH_HOLDER -> "There is an Authorization Holder";
            case CONSIGNMENT_VALUATION_ADJUSTMENTS -> "There exists a Valuation Adjustment";
            default -> "Unknown anchor exists";
        };
    }

    // existing: renderClause(...), prettyLabel(...), operatorWord(...), etc.
}
