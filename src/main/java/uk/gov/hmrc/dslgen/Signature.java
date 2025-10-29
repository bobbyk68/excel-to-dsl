// ====================== ParsedClause ======================
final class ParsedClause {
    private final String path;          // e.g., "GoodsItem.specialProcedures.code"
    private final String opToken;       // e.g., "equals", "is one of", "exists", "not in"
    private final java.util.List<String> values;

    ParsedClause(String path, String opToken, java.util.List<String> values) {
        this.path = path;
        this.opToken = opToken;
        this.values = values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }
    String path() { return path; }
    String opToken() { return opToken; }
    java.util.List<String> values() { return values; }
}

// ====================== Signature ======================
final class Signature {

    enum Anchor {
        GOODS_ITEM,
        GI_SPECIAL_PROCEDURES,
        GI_ADDITIONAL_DOCUMENTS,
        GI_ADDITIONAL_INFORMATION,
        GI_DECLARED_DUTY_TAX_FEES,
        GI_ORIGIN,
        DECL_AUTH_HOLDER,
        CONSIGNMENT_VALUATION_ADJUSTMENTS,
        UNKNOWN
    }

    enum OpFamily { EQ, NEX, IN, NIN, EX }

    static final class Kind {
        enum Card { NONE, ONE, MANY }
        final OpFamily op;
        final Card card;
        Kind(OpFamily op, Card card) { this.op = op; this.card = card; }
    }

    private final Anchor ifAnchor;
    private final Anchor thenAnchor;
    private final boolean mergeable;

    // NEW
    private final boolean crossInstance;
    private final boolean negateThen;

    final Kind ifKind;
    final Kind thenKind;

    Signature(Anchor ifAnchor,
              Anchor thenAnchor,
              boolean mergeable,
              Kind ifKind,
              Kind thenKind,
              boolean crossInstance,
              boolean negateThen) {
        this.ifAnchor = ifAnchor;
        this.thenAnchor = thenAnchor;
        this.mergeable = mergeable;
        this.ifKind = ifKind;
        this.thenKind = thenKind;
        this.crossInstance = crossInstance;
        this.negateThen = negateThen;
    }

    Anchor ifAnchor() { return ifAnchor; }
    Anchor thenAnchor() { return thenAnchor; }
    boolean mergeable() { return mergeable; }
    boolean crossInstance() { return crossInstance; }
    boolean negateThen() { return negateThen; }
}

// ====================== SignatureBuilder ======================
final class SignatureBuilder {

    Signature build(String ruleId, ParsedClause ifC, ParsedClause thenC) {
        var ifAnchor = resolveAnchor(ifC.path());
        var thenAnchor = resolveAnchor(thenC.path());

        var ifKind = new Signature.Kind(normalizeOp(ifC.opToken()),
                cardinalityOf(ifC.values()));
        var thenKind = new Signature.Kind(normalizeOp(thenC.opToken()),
                cardinalityOf(thenC.values()));

        boolean mergeable = (ifAnchor == thenAnchor);

        // --- cross-instance heuristic (v1): same collection anchor + same leaf field
        boolean crossInstance = false;
        if (isCollection(ifAnchor) && ifAnchor == thenAnchor) {
            String leafIf = lastSegment(ifC.path());
            String leafThen = lastSegment(thenC.path());
            if (!leafIf.isEmpty() && leafIf.equals(leafThen)) {
                crossInstance = true;
                mergeable = false; // cannot bind both constraints to one child row
            }
        }

        // --- negateThen policy
        boolean negateThen = true; // default implication A -> NOT B
        if (crossInstance) {
            switch (thenKind.op) {
                case EQ, IN, EX -> negateThen = false; // co-existence
                case NEX, NIN -> negateThen = true;    // exclusion
            }
        }

        return new Signature(ifAnchor, thenAnchor, mergeable, ifKind, thenKind, crossInstance, negateThen);
    }

    // ---------- helpers ----------
    private Signature.Kind.Card cardinalityOf(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return Signature.Kind.Card.NONE;
        return values.size() == 1 ? Signature.Kind.Card.ONE : Signature.Kind.Card.MANY;
    }

    private boolean isCollection(Signature.Anchor a) {
        return a == Signature.Anchor.GI_SPECIAL_PROCEDURES
                || a == Signature.Anchor.GI_ADDITIONAL_DOCUMENTS
                || a == Signature.Anchor.GI_ADDITIONAL_INFORMATION
                || a == Signature.Anchor.CONSIGNMENT_VALUATION_ADJUSTMENTS;
    }

    private static String lastSegment(String path) {
        if (path == null || path.isBlank()) return "";
        int i = path.lastIndexOf('.');
        return (i >= 0 && i + 1 < path.length()) ? path.substring(i + 1) : path;
    }

    private Signature.Anchor resolveAnchor(String path) {
        if (path == null) return Signature.Anchor.UNKNOWN;
        String p = path;
        // normalise spacing
        p = p.trim();

        if (p.startsWith("GoodsItem.specialProcedures."))  return Signature.Anchor.GI_SPECIAL_PROCEDURES;
        if (p.startsWith("GoodsItem.additionalDocuments."))return Signature.Anchor.GI_ADDITIONAL_DOCUMENTS;
        if (p.startsWith("GoodsItem.additionalInformation.")) return Signature.Anchor.GI_ADDITIONAL_INFORMATION;
        if (p.startsWith("GoodsItem."))                    return Signature.Anchor.GOODS_ITEM;
        if (p.startsWith("ConsignmentShipment.valuationAdjustments.")) return Signature.Anchor.CONSIGNMENT_VALUATION_ADJUSTMENTS;
        if (p.startsWith("AuthorizationHolder."))          return Signature.Anchor.DELCL_AUTH_HOLDER; // if your real root differs, adjust
        // add other roots as needed
        return Signature.Anchor.UNKNOWN;
    }

    private Signature.OpFamily normalizeOp(String opToken) {
        if (opToken == null) return Signature.OpFamily.EQ;
        String s = opToken.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.contains("does not exist") || s.equals("not exists")) return Signature.OpFamily.NEX;
        if (s.equals("exists") || s.equals("is present") || s.equals("present")) return Signature.OpFamily.EX;

        if (s.contains("not in") || s.contains("must not be one of")) return Signature.OpFamily.NIN;
        if (s.contains("in") || s.contains("is one of") || s.contains("must be one of")) return Signature.OpFamily.IN;

        if (s.contains("not equals") || s.contains("must not equal") || s.equals("is not")) return Signature.OpFamily.NEX;
        // equals / is / equal to
        return Signature.OpFamily.EQ;
    }
}

// ====================== Router ======================
final class Router {

    static final class RouteResult {
        private final boolean ok;
        private final String reason;
        private RouteResult(boolean ok, String reason) { this.ok = ok; this.reason = reason; }
        static RouteResult success() { return new RouteResult(true, ""); }
        static RouteResult fail(String reason) { return new RouteResult(false, reason); }
        boolean ok() { return ok; }
        String reason() { return reason; }
    }

    RouteResult route(Signature sig) {
        if (sig.ifAnchor() == Signature.Anchor.UNKNOWN || sig.thenAnchor() == Signature.Anchor.UNKNOWN) {
            return RouteResult.fail("UNKNOWN_ANCHOR");
        }
        // Allow crossInstance for collection anchors; no special block.
        return RouteResult.success();
    }
}

// ====================== RuleIR ======================
final class RuleIR {

    static final class Clause {
        final String path;                  // dotted path (domain)
        final Signature.OpFamily op;        // already negated if needed by emitter
        final Signature.Kind.Card card;
        final java.util.List<String> values;

        Clause(String path, Signature.OpFamily op, Signature.Kind.Card card, java.util.List<String> values) {
            this.path = path;
            this.op = op;
            this.card = card;
            this.values = values == null ? java.util.List.of() : java.util.List.copyOf(values);
        }
    }

    static final class Section {
        final Signature.Anchor anchor;
        final java.util.List<Clause> clauses;
        Section(Signature.Anchor anchor, java.util.List<Clause> clauses) {
            this.anchor = anchor;
            this.clauses = java.util.List.copyOf(clauses);
        }
    }

    static final class ThenEffect {
        enum Type { ERROR_CODE }
        final Type type;
        final java.util.List<String> values;
        private ThenEffect(Type type, java.util.List<String> values) {
            this.type = type;
            this.values = java.util.List.copyOf(values);
        }
        static ThenEffect errorCodes(java.util.List<String> codes) {
            return new ThenEffect(Type.ERROR_CODE, codes);
        }
    }

    final java.util.List<Section> sections = new java.util.ArrayList<>();
    final java.util.List<ThenEffect> thenEffects = new java.util.ArrayList<>();

    // NEW: signal for formatter
    private boolean crossInstance;

    void addSection(Signature.Anchor a, java.util.List<Clause> cs) { sections.add(new Section(a, cs)); }
    void addErrorCodes(java.util.List<String> codes) { thenEffects.add(ThenEffect.errorCodes(codes)); }

    void setCrossInstance(boolean v) { this.crossInstance = v; }
    boolean isCrossInstance() { return crossInstance; }
}

// ====================== GenericEmitter ======================
final class GenericEmitter {

    // Minimal emit context (what collectAll builds)
    static final class EmitContext {
        final RuleIR.Clause ifClause;   // op is positive as parsed
        final RuleIR.Clause thenClause; // op is as parsed (not yet flipped)
        final java.util.List<String> errorCodes;

        EmitContext(RuleIR.Clause ifClause, RuleIR.Clause thenClause, java.util.List<String> errorCodes) {
            this.ifClause = ifClause;
            this.thenClause = thenClause;
            this.errorCodes = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
        }
    }

    RuleIR emit(Signature sig, EmitContext ctx) {
        // flip THEN op if signature says so
        var thenOp = ctx.thenClause.op;
        if (sig.negateThen()) {
            thenOp = switch (thenOp) {
                case EQ  -> Signature.OpFamily.NEX;
                case IN  -> Signature.OpFamily.NIN;
                case EX  -> Signature.OpFamily.NEX;   // not exists
                case NEX -> Signature.OpFamily.EQ;
                case NIN -> Signature.OpFamily.IN;
            };
        }

        var thenAdj = new RuleIR.Clause(ctx.thenClause.path, thenOp, ctx.thenClause.card, ctx.thenClause.values);

        RuleIR ir = new RuleIR();
        if (sig.mergeable()) {
            ir.addSection(sig.ifAnchor(), java.util.List.of(
                    new RuleIR.Clause(ctx.ifClause.path, ctx.ifClause.op, ctx.ifClause.card, ctx.ifClause.values),
                    thenAdj
            ));
        } else {
            ir.addSection(sig.ifAnchor(), java.util.List.of(
                    new RuleIR.Clause(ctx.ifClause.path, ctx.ifClause.op, ctx.ifClause.card, ctx.ifClause.values)
            ));
            ir.addSection(sig.thenAnchor(), java.util.List.of(thenAdj));
        }
        ir.setCrossInstance(sig.crossInstance());
        if (!ctx.errorCodes.isEmpty()) ir.addErrorCodes(ctx.errorCodes);
        return ir;
    }
}

// ====================== DslrFormatter ======================
final class DslrFormatter {

    public String toDslr(RuleIR ir) {
        StringBuilder sb = new StringBuilder();
        sb.append("when\n");

        if (ir.sections.size() == 1) {
            var s = ir.sections.get(0);
            sb.append("  ").append(parentStem(s.anchor, /*matching*/ false, /*cross*/ false)).append("\n");
            for (var c : sortClauses(s.clauses)) {
                sb.append("    - ").append(renderClause(c)).append("\n");
            }
        } else if (ir.sections.size() == 2) {
            var s1 = ir.sections.get(0);
            var s2 = ir.sections.get(1);

            sb.append("  ").append(parentStem(s1.anchor, false, ir.isCrossInstance())).append("\n");
            sb.append("    - ").append(renderClause(s1.clauses.get(0))).append("\n\n");

            boolean matching = ir.isCrossInstance()
                    && s1.anchor == Signature.Anchor.GI_SPECIAL_PROCEDURES
                    && s2.anchor == Signature.Anchor.GI_SPECIAL_PROCEDURES;

            if (matching) {
                sb.append("  ").append("Matching ").append(baseStem(s2.anchor)).append("\n");
            } else {
                sb.append("  ").append(parentStem(s2.anchor, false, ir.isCrossInstance())).append("\n");
            }
            sb.append("    - ").append(renderClause(s2.clauses.get(0))).append("\n");
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

    // ---------- wording helpers ----------
    private java.util.List<RuleIR.Clause> sortClauses(java.util.List<RuleIR.Clause> cs) {
        return cs.stream()
                .sorted(java.util.Comparator
                        .comparing((RuleIR.Clause c) -> c.path)
                        .thenComparing(c -> c.op.name()))
                .collect(java.util.stream.Collectors.toList());
    }

    private String parentStem(Signature.Anchor a, boolean isMatching, boolean crossInstance) {
        if (isMatching) return "Matching " + baseStem(a);
        return baseStem(a);
    }

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

    private String renderClause(RuleIR.Clause c) {
        boolean existential = (c.op == Signature.OpFamily.EX) ||
                (c.op == Signature.OpFamily.NEX && (c.values == null || c.values.isEmpty()));
        String label = prettyLabel(c.path);

        if (existential) {
            String prefix = (c.op == Signature.OpFamily.EX) ? "with" : "without";
            return prefix + " " + label + " exists";
        }
        // value comparisons: always "with"
        String opWord = operatorWord(c.op);
        String vals = renderValuesNoBrackets(c.values);
        return "with " + label + " " + opWord + " " + vals;
    }

    private String operatorWord(Signature.OpFamily op) {
        return switch (op) {
            case EQ -> "equals";
            case NEX -> "not equals";
            case IN -> "in";
            case NIN -> "not in";
            case EX -> "exists";
        };
    }

    private String renderValuesNoBrackets(java.util.List<String> vals) {
        if (vals == null || vals.isEmpty()) return "\"\"";
        if (vals.size() == 1) return quote(vals.get(0));
        return vals.stream().map(this::quote).collect(java.util.stream.Collectors.joining(","));
    }
    private String quote(String s) { return "\"" + s + "\""; }

    // ---------- pretty label ----------
    private String prettyLabel(String dottedPath) {
        if (dottedPath == null || dottedPath.isBlank()) return "";
        String p = dottedPath.trim();
        int i = p.indexOf('.');
        String tail = (i >= 0 && i + 1 < p.length()) ? p.substring(i + 1) : p; // drop root

        String[] toks = tail.split("\\.");
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String t : toks) {
            if (t.isBlank()) continue;
            for (String w : deCamel(t)) words.add(w);
        }
        // lowercase except known acronyms
        java.util.Set<String> acr = java.util.Set.of("AEO", "EORI", "VAT", "UCR");
        for (int k = 0; k < words.size(); k++) {
            String up = words.get(k).toUpperCase(java.util.Locale.ROOT);
            if (acr.contains(up)) words.set(k, up);
            else words.set(k, words.get(k).toLowerCase(java.util.Locale.ROOT));
        }
        if (!words.isEmpty() && "value".equalsIgnoreCase(words.get(words.size()-1))) {
            words.remove(words.size()-1);
        }
        return String.join(" ", words);
    }

    private java.util.List<String> deCamel(String token) {
        String spaced = token
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
        String[] parts = spaced.split("[_\\s]+");
        return java.util.Arrays.asList(parts);
    }
}
