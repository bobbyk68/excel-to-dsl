package uk.gov.hmrc.dslgen;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DslrGen — single-file, minimal pipeline:
 *   AtomicHit -> SignatureBuilder -> Router -> GenericEmitter (negates THEN) -> DslrFormatter
 *
 * Shapes:
 *   - mergeable = true  -> one parent + two dash lines
 *   - mergeable = false -> two parents, one dash each
 *
 * No seq/join lines printed; DSL macros are assumed to bind seq implicitly.
 * Version: 1.0.0 (2025-10-23)
 */
public final class DslrGen {

    // ─────────────────────────────────────────────────────────────────────────────
    //  Public types you’ll use from collectAll() (tiny DTOs)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Minimal clause parsed from your sheet/JSON. */
    public record ParsedClause(String path, String opToken, List<String> values) { }

    /** Simple “atomic hit” DTO you can adapt to your parser. */
    public record AtomicHit(String ruleId,
                            ParsedClause ifClause,
                            ParsedClause thenClause,
                            List<String> errorCodes) { }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Signature model + builder (facts only; no policy)
    // ─────────────────────────────────────────────────────────────────────────────

    public static final class Signature {
        public enum Scope { GOODS_ITEM, HEADER, CONSIGNMENT, MIXED, UNKNOWN }
        public enum Anchor {
            GOODS_ITEM,
            GI_SPECIAL_PROCEDURES,
            GI_ADDITIONAL_INFORMATION,
            GI_ADDITIONAL_DOCUMENTS,
            GI_DECLARED_DUTY_TAX_FEES,
            GI_ORIGIN,
            DECL_AUTH_HOLDER,
            CONSIGNMENT_VALUATION_ADJUSTMENTS,
            UNKNOWN
        }
        public enum OpFamily { EQ, IN, NIN, EX, NEX }
        public enum Card { ONE, MANY }
        public enum Polarity { POS, NEG }

        /** Kind summary for IF/THEN (used by emitter). */
        public static final class Kind {
            public final String family;     // RP|PP|SP|AD|AI|FIELD
            public final OpFamily op;
            public final Card card;
            public final Polarity polarity;
            public Kind(String family, OpFamily op, Card card, Polarity polarity) {
                this.family = family; this.op = op; this.card = card; this.polarity = polarity;
            }
        }

        public final Scope scope;
        public final Anchor ifAnchor;
        public final Anchor thenAnchor;
        public final boolean mergeable;
        public final Kind ifKind;
        public final Kind thenKind;
        public final Set<String> flags;
        public final String ruleId;

        public Signature(Scope scope, Anchor ifAnchor, Anchor thenAnchor,
                         boolean mergeable, Kind ifKind, Kind thenKind,
                         Set<String> flags, String ruleId) {
            this.scope = scope;
            this.ifAnchor = ifAnchor;
            this.thenAnchor = thenAnchor;
            this.mergeable = mergeable;
            this.ifKind = Objects.requireNonNull(ifKind);
            this.thenKind = Objects.requireNonNull(thenKind);
            this.flags = Set.copyOf(flags == null ? new LinkedHashSet<>() : flags);
            this.ruleId = ruleId;
        }
    }

    /** Table-driven mapping from path prefixes -> anchors (+ seq-aware whitelist). */
    public static final class AnchorRegistry {
        private AnchorRegistry() {}
        // order: most-specific first
        private static final List<Map.Entry<String, Signature.Anchor>> PREFIX_TO_ANCHOR = List.of(
            Map.entry("goodsitem.additionaldocuments.", Signature.Anchor.GI_ADDITIONAL_DOCUMENTS),
            Map.entry("goodsitem.additionalinformation.", Signature.Anchor.GI_ADDITIONAL_INFORMATION),
            Map.entry("goodsitem.specialprocedures.",   Signature.Anchor.GI_SPECIAL_PROCEDURES),
            Map.entry("goodsitem.declareddutytaxfees.", Signature.Anchor.GI_DECLARED_DUTY_TAX_FEES),
            Map.entry("goodsitem.origin.",              Signature.Anchor.GI_ORIGIN),
            Map.entry("goodsitem.",                     Signature.Anchor.GOODS_ITEM),
            Map.entry("declaration.authorizationholder.", Signature.Anchor.DECL_AUTH_HOLDER),
            Map.entry("consignmentshipment.valuationadjustments.", Signature.Anchor.CONSIGNMENT_VALUATION_ADJUSTMENTS)
        );
        private static final Set<Signature.Anchor> SEQ_AWARE = Set.of(
            Signature.Anchor.GOODS_ITEM,
            Signature.Anchor.GI_ADDITIONAL_DOCUMENTS,
            Signature.Anchor.GI_ADDITIONAL_INFORMATION,
            Signature.Anchor.GI_SPECIAL_PROCEDURES,
            Signature.Anchor.GI_DECLARED_DUTY_TAX_FEES,
            Signature.Anchor.GI_ORIGIN,
            Signature.Anchor.DECL_AUTH_HOLDER,
            Signature.Anchor.CONSIGNMENT_VALUATION_ADJUSTMENTS
        );
        public static Signature.Anchor inferAnchor(String dottedPath) {
            if (dottedPath == null) return Signature.Anchor.UNKNOWN;
            String p = dottedPath.trim().toLowerCase(Locale.ROOT);
            for (var e : PREFIX_TO_ANCHOR) if (p.startsWith(e.getKey())) return e.getValue();
            return Signature.Anchor.UNKNOWN;
        }
        public static boolean isSeqAware(Signature.Anchor a) { return SEQ_AWARE.contains(a); }
    }

    /** Normalize operator phrases to small enum set. */
    public static final class OperatorLexicon {
        private OperatorLexicon() {}
        private static final Map<String, Signature.OpFamily> TOKENS;
        static {
            Map<String, Signature.OpFamily> m = new HashMap<>();
            reg(m, Signature.OpFamily.EQ,  "=", "==", "equals", "equal to", "is", "must equal");
            reg(m, Signature.OpFamily.NEX, "!=", "<>", "not equals", "not equal", "is not", "must not equal");
            reg(m, Signature.OpFamily.IN,  "in", "is one of", "one of", "must be one of");
            reg(m, Signature.OpFamily.NIN, "not in", "must not be one of", "must not be in", "not one of");
            reg(m, Signature.OpFamily.EX,  "exists", "is present", "present");
            reg(m, Signature.OpFamily.NEX, "not exists", "does not exist", "is absent", "absent");
            TOKENS = Collections.unmodifiableMap(m);
        }
        public static Signature.OpFamily requireFamily(String token) {
            String k = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            var fam = TOKENS.get(k);
            if (fam == null) throw new IllegalArgumentException("Unknown operator token: " + token);
            return fam;
        }
        private static void reg(Map<String, Signature.OpFamily> m, Signature.OpFamily fam, String... toks) {
            for (String t : toks) m.put(t.toLowerCase(Locale.ROOT), fam);
        }
    }

    /** Builds Signature from two ParsedClause lines (IF + THEN). */
    public static final class SignatureBuilder {
        public Signature build(String ruleId, ParsedClause ifC, ParsedClause thenC) {
            var ifAnchor   = AnchorRegistry.inferAnchor(ifC.path());
            var thenAnchor = AnchorRegistry.inferAnchor(thenC.path());
            var scopeIf    = scopeOf(ifAnchor);
            var scopeThen  = scopeOf(thenAnchor);
            var scope      = (scopeIf == scopeThen) ? scopeIf : Signature.Scope.MIXED;

            boolean mergeable  = (ifAnchor == thenAnchor);
            boolean seqAwareIF = AnchorRegistry.isSeqAware(ifAnchor);
            boolean seqAwareTH = AnchorRegistry.isSeqAware(thenAnchor);

            var ifOp   = OperatorLexicon.requireFamily(ifC.opToken());
            var thenOp = OperatorLexicon.requireFamily(thenC.opToken());

            Set<String> flags = new LinkedHashSet<>();
            if (!seqAwareIF || !seqAwareTH) flags.add("NO_KEY");
            if (usesSentinel(ifC.values()) || usesSentinel(thenC.values())) flags.add("SENTINEL_BLOCKED");
            if (ifAnchor == Signature.Anchor.UNKNOWN || thenAnchor == Signature.Anchor.UNKNOWN) flags.add("UNKNOWN_ANCHOR");

            var ifKind   = new Signature.Kind(familyOf(ifC.path()), ifOp,   cardOf(ifC.values()), Signature.Polarity.POS);
            var thenKind = new Signature.Kind(familyOf(thenC.path()), thenOp, cardOf(thenC.values()), Signature.Polarity.POS);

            return new Signature(scope, ifAnchor, thenAnchor, mergeable, ifKind, thenKind, flags, ruleId);
        }

        private Signature.Scope scopeOf(Signature.Anchor a) {
            return switch (a) {
                case GOODS_ITEM, GI_ADDITIONAL_DOCUMENTS, GI_ADDITIONAL_INFORMATION,
                     GI_SPECIAL_PROCEDURES, GI_DECLARED_DUTY_TAX_FEES, GI_ORIGIN -> Signature.Scope.GOODS_ITEM;
                case DECL_AUTH_HOLDER -> Signature.Scope.HEADER;
                case CONSIGNMENT_VALUATION_ADJUSTMENTS -> Signature.Scope.CONSIGNMENT;
                case UNKNOWN -> Signature.Scope.UNKNOWN;
            };
        }
        private String familyOf(String path) {
            if (path == null) return "FIELD";
            String p = path.toLowerCase(Locale.ROOT);
            if (p.contains("requestedprocedure"))  return "RP";
            if (p.contains("previousprocedure"))   return "PP";
            if (p.contains("additionaldocuments")) return "AD";
            if (p.contains("additionalinformation")) return "AI";
            if (p.contains("specialprocedures"))   return "SP";
            return "FIELD";
        }
        private Signature.Card cardOf(List<String> values) {
            return (values != null && values.size() > 1) ? Signature.Card.MANY : Signature.Card.ONE;
        }
        private boolean usesSentinel(List<String> values) {
            if (values == null) return false;
            for (String v : values) {
                String s = v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
                if (s.equals("NONE") || s.equals("ALL") || s.equals("ANY") || s.equals("N/A")) return true;
            }
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Router (tiny policy gate)
    // ─────────────────────────────────────────────────────────────────────────────

    public static final class Router {
        public record RouteResult(boolean ok, String reason) {
            public static RouteResult ok() { return new RouteResult(true, null); }
            public static RouteResult fail(String reason) { return new RouteResult(false, reason); }
        }
        public RouteResult route(Signature sig) {
            if (sig.flags.contains("UNKNOWN_ANCHOR"))   return RouteResult.fail("UNKNOWN_ANCHOR");
            if (sig.flags.contains("SENTINEL_BLOCKED")) return RouteResult.fail("SENTINEL_BLOCKED");
            if (sig.flags.contains("NO_KEY"))           return RouteResult.fail("NO_KEY");
            return RouteResult.ok();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Intermediate representation (IR) + Emitter (negates THEN)
    // ─────────────────────────────────────────────────────────────────────────────

    public static final class RuleIR {
        public static final class Clause {
            public final String path;
            public final Signature.OpFamily op;
            public final Signature.Polarity polarity;
            public final Signature.Card card;
            public final List<String> values;
            public Clause(String path, Signature.OpFamily op, Signature.Polarity polarity,
                          Signature.Card card, List<String> values) {
                this.path = path; this.op = op; this.polarity = polarity; this.card = card; this.values = values;
            }
        }
        public static final class Section {
            public final Signature.Anchor anchor;
            public final List<Clause> clauses; // if mergeable -> size 2; else -> size 1
            public Section(Signature.Anchor anchor, List<Clause> clauses) { this.anchor = anchor; this.clauses = clauses; }
        }
        public static final class ThenEffect {
            public enum Type { ERROR_CODE }
            public final Type type;
            public final List<String> values;
            public ThenEffect(Type type, List<String> values) { this.type = type; this.values = values; }
            public static ThenEffect errorCodes(List<String> codes) { return new ThenEffect(Type.ERROR_CODE, codes); }
        }
        public final List<Section> sections;
        public final List<ThenEffect> thenEffects;
        public final Set<String> flags;
        public final String ruleId;
        public RuleIR(List<Section> sections, List<ThenEffect> thenEffects, Set<String> flags, String ruleId) {
            this.sections = sections; this.thenEffects = thenEffects; this.flags = flags; this.ruleId = ruleId;
        }
    }

    /** Single emitter that applies THEN-negation and constructs IR sections. */
    public static final class GenericEmitter {
        public RuleIR emit(Signature sig, EmitContext ctx) {
            var whenIf = new RuleIR.Clause(
                ctx.ifClause.path, ctx.ifClause.opFamily, Signature.Polarity.POS, ctx.ifClause.card, ctx.ifClause.values);
            var whenThenNeg = new RuleIR.Clause(
                ctx.thenClause.path, negate(sig.thenKind.op), Signature.Polarity.NEG, ctx.thenClause.card, ctx.thenClause.values);

            var sections = new ArrayList<RuleIR.Section>(2);
            if (sig.mergeable) {
                sections.add(new RuleIR.Section(sig.ifAnchor, List.of(whenIf, whenThenNeg)));
            } else {
                sections.add(new RuleIR.Section(sig.ifAnchor, List.of(whenIf)));
                sections.add(new RuleIR.Section(sig.thenAnchor, List.of(whenThenNeg)));
            }
            var effects = (ctx.thenEffects == null) ? List.<RuleIR.ThenEffect>of() : ctx.thenEffects;
            return new RuleIR(sections, effects, sig.flags, sig.ruleId);
        }
        private static Signature.OpFamily negate(Signature.OpFamily op) {
            return switch (op) {
                case EQ  -> Signature.OpFamily.NEX;
                case IN  -> Signature.OpFamily.NIN;
                case NIN -> Signature.OpFamily.IN;
                case EX  -> Signature.OpFamily.NEX;
                case NEX -> Signature.OpFamily.EX;
            };
        }
        public static final class EmitContext {
            public final Clause ifClause;
            public final Clause thenClause;
            public final List<RuleIR.ThenEffect> thenEffects;
            public EmitContext(Clause ifClause, Clause thenClause, List<RuleIR.ThenEffect> thenEffects) {
                this.ifClause = ifClause; this.thenClause = thenClause; this.thenEffects = thenEffects;
            }
        }
        public static final class Clause {
            public final String path;
            public final Signature.OpFamily opFamily;
            public final Signature.Card card;
            public final List<String> values;
            public Clause(String path, Signature.OpFamily opFamily, Signature.Card card, List<String> values) {
                this.path = path; this.opFamily = opFamily; this.card = card; this.values = values;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  DSLR formatter (two shapes only; no seq joins printed)
    // ─────────────────────────────────────────────────────────────────────────────

    public static final class DslrFormatter {
        public String toDslr(RuleIR ir) {
            StringBuilder sb = new StringBuilder();
            sb.append("when\n");

            if (ir.sections.size() == 1) {
                var sec = ir.sections.get(0);
                sb.append("  ").append(parentStem(sec.anchor)).append("\n");
                for (var cl : sortClauses(sec.clauses)) {
                    sb.append("    - ").append(render(cl)).append("\n");
                }
            } else if (ir.sections.size() == 2) {
                var a = ir.sections.get(0);
                var b = ir.sections.get(1);
                sb.append("  ").append(parentStem(a.anchor)).append("\n");
                sb.append("    - ").append(render(a.clauses.get(0))).append("\n\n");
                sb.append("  ").append("[and]").append("\n");
                sb.append("  ").append(parentStem(b.anchor)).append("\n");
                sb.append("    - ").append(render(b.clauses.get(0))).append("\n");
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

        private List<RuleIR.Clause> sortClauses(List<RuleIR.Clause> clauses) {
            return clauses.stream()
                    .sorted(Comparator
                            .comparing((RuleIR.Clause c) -> c.path)
                            .thenComparing(c -> c.op.name())
                            .thenComparing(c -> c.values.isEmpty() ? "" : c.values.get(0)))
                    .collect(Collectors.toList());
        }
        private String render(RuleIR.Clause c) {
            return switch (c.op) {
                case EQ  -> c.path + " equals "     + renderVals(c.values, false);
                case NEX -> c.path + " not equals " + renderVals(c.values, false);
                case IN  -> c.path + " in "         + renderVals(c.values, true);
                case NIN -> c.path + " not in "     + renderVals(c.values, true);
                case EX  -> c.path + " exists";
            };
        }
        private String renderVals(List<String> vals, boolean array) {
            if (vals == null || vals.isEmpty()) return "\"\"";
            if (!array && vals.size() == 1) return quote(vals.get(0));
            return "[" + vals.stream().map(this::quote).collect(Collectors.joining(",")) + "]";
        }
        private String quote(String s) { return "\"" + s + "\""; }
        private String parentStem(Signature.Anchor a) {
            return switch (a) {
                case GOODS_ITEM -> "There is a Goods Item";
                case GI_SPECIAL_PROCEDURES -> "There exists Special Procedure";
                case GI_ADDITIONAL_INFORMATION -> "There exists Additional Information";
                case GI_ADDITIONAL_DOCUMENTS -> "There exists Additional Document";
                case GI_DECLARED_DUTY_TAX_FEES -> "There exists Declared Duty/Tax/Fee";
                case GI_ORIGIN -> "There exists Origin";
                case DECL_AUTH_HOLDER -> "There is an Authorization Holder";
                case CONSIGNMENT_VALUATION_ADJUSTMENTS -> "There exists a Valuation Adjustment";
                default -> "There exists Unknown Anchor";
            };
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Tiny runner (optional): shows the flow on one sample atomic hit
    // ─────────────────────────────────────────────────────────────────────────────

    public static void runExample() {
        var hit = new AtomicHit(
            "R1",
            new ParsedClause("GoodsItem.requestedProcedure.code", "equals", List.of("VAL1")),
            new ParsedClause("GoodsItem.previousProcedure.code", "is one of", List.of("VAL2","VAL3")),
            List.of("ERRCODE_R1")
        );

        var sig = new SignatureBuilder().build(hit.ruleId(), hit.ifClause(), hit.thenClause());
        var rr  = new Router().route(sig);
        if (!rr.ok()) {
            System.out.println("// fail-fast: " + rr.reason());
            return;
        }

        var ctx = new GenericEmitter.EmitContext(
            new GenericEmitter.Clause("requestedProcedure.code", sig.ifKind.op, sig.ifKind.card, hit.ifClause.values()),
            new GenericEmitter.Clause("previousProcedure.code",   sig.thenKind.op, sig.thenKind.card, hit.thenClause.values()),
            List.of(RuleIR.ThenEffect.errorCodes(hit.errorCodes()))
        );
        var ir   = new GenericEmitter().emit(sig, ctx);
        var dslr = new DslrFormatter().toDslr(ir);
        System.out.println(dslr);
    }
}
