/*
 * Module: rules-emitter
 * File: Signature.java
 * @version: 1.7.0  (release: 2025-10-30)
 * @since:   1.5.0
 * Owner:    BK/RK
 *
 * Summary:
 * Immutable row-level intent parsed from Excel. Carries IF/THEN
 * paths/ops/values + flags guiding emission behavior.
 *
 * Change Highlights:
 * - 1.7.0: Added/Restored flags: mergeable, crossInstance, negateThen.
 */

public record Signature(
        // IF
        String ifPath,
        String ifOpSymbol,
        java.util.List<String> ifValues,

        // THEN
        String thenPath,
        String thenOpSymbol,
        java.util.List<String> thenValues,

        // Flags
        boolean mergeable,      // true = rows of same shape can be grouped
        boolean crossInstance,  // true = IF and THEN must be satisfied by distinct instances
        boolean negateThen,     // true = THEN is logically negated ("must not ...")

        // Effects / passthrough
        java.util.List<String> errorCodes,
        String procCategory,
        String decType
) {
    public Signature {
        ifPath       = ifPath == null ? "" : ifPath;
        ifOpSymbol   = ifOpSymbol == null ? "" : ifOpSymbol;
        ifValues     = ifValues == null ? java.util.List.of() : java.util.List.copyOf(ifValues);
        thenPath     = thenPath == null ? "" : thenPath;
        thenOpSymbol = thenOpSymbol == null ? "" : thenOpSymbol;
        thenValues   = thenValues == null ? java.util.List.of() : java.util.List.copyOf(thenValues);
        errorCodes   = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
    }

    // Convenience
    public boolean ifIsExists()      { return "exists".equalsIgnoreCase(ifOpSymbol); }
    public boolean thenIsExists()    { return "exists".equalsIgnoreCase(thenOpSymbol); }
    public boolean ifHasNoValues()   { return ifValues.isEmpty(); }
    public boolean thenHasNoValues() { return thenValues.isEmpty(); }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.0 (2025-10-30): Flags restored: mergeable, crossInstance, negateThen.
 * 1.6.0 (2025-10-21): Converted to record; normalized list immutability.
 * --------------------------------------------------------------------
 */





/*
 * Module: rules-emitter
 * File: SignatureBuilder.java
 * @version: 1.7.0  (release: 2025-10-30)
 * @since:   1.5.0
 * Owner:    BK/RK
 *
 * Summary:
 * Fluent builder for Signature with sane defaults and operator normalization.
 */

public final class SignatureBuilder {
    private String ifPath = "", ifOp = "";
    private java.util.List<String> ifVals = java.util.List.of();

    private String thenPath = "", thenOp = "";
    private java.util.List<String> thenVals = java.util.List.of();

    private boolean mergeable = false;
    private boolean crossInstance = false;
    private boolean negateThen = false;

    private java.util.List<String> errorCodes = java.util.List.of();
    private String procCategory = null;
    private String decType = null;

    public SignatureBuilder ifPath(String v){ this.ifPath=v; return this; }
    public SignatureBuilder ifOp(String v){ this.ifOp=norm(v); return this; }
    public SignatureBuilder ifValues(java.util.List<String> v){ this.ifVals=copy(v); return this; }

    public SignatureBuilder thenPath(String v){ this.thenPath=v; return this; }
    public SignatureBuilder thenOp(String v){ this.thenOp=norm(v); return this; }
    public SignatureBuilder thenValues(java.util.List<String> v){ this.thenVals=copy(v); return this; }

    public SignatureBuilder mergeable(boolean v){ this.mergeable=v; return this; }
    public SignatureBuilder crossInstance(boolean v){ this.crossInstance=v; return this; }
    public SignatureBuilder negateThen(boolean v){ this.negateThen=v; return this; }

    public SignatureBuilder errorCodes(java.util.List<String> v){ this.errorCodes=copy(v); return this; }
    public SignatureBuilder procCategory(String v){ this.procCategory=v; return this; }
    public SignatureBuilder decType(String v){ this.decType=v; return this; }

    public Signature build(){
        return new Signature(ifPath, ifOp, ifVals, thenPath, thenOp, thenVals,
                mergeable, crossInstance, negateThen, errorCodes, procCategory, decType);
    }

    // --- helpers ---
    private static String norm(String s){
        if (s==null) return "";
        String t=s.trim().toLowerCase();
        return switch (t){
            case "equals","eq","==" -> "==";
            case "in"               -> "in";
            case "exists"           -> "exists";
            case "!=", "<>", "not equals" -> "!=";
            case "not in"           -> "not in";
            default -> s;
        };
    }
    private static java.util.List<String> copy(java.util.List<String> v){
        return v==null?java.util.List.of():java.util.List.copyOf(v);
    }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.0 (2025-10-30): Restored flags; operator normalization helper.
 * --------------------------------------------------------------------
 */


/*
 * Module: rules-emitter
 * File: EmitContext.java
 * @version: 1.7.0  (release: 2025-10-30)
 * @since:   1.6.0
 * Owner:    BK/RK
 *
 * Summary:
 * Immutable payload handed to emitters: IF/THEN clauses, flags, IDs.
 */

public final class EmitContext {

    // Small nested value object for a clause
    public static final class Clause {
        private final String path;
        private final String op;
        private final java.util.List<String> values;

        public Clause(String path, String op, java.util.List<String> values) {
            this.path   = path   == null ? "" : path;
            this.op     = op     == null ? "" : op;
            this.values = values == null ? java.util.List.of() : java.util.List.copyOf(values);
        }
        public String path() { return path; }
        public String op() { return op; }
        public java.util.List<String> values() { return values; }
        public boolean isExists() { return "exists".equalsIgnoreCase(op); }
    }

    private final String ruleId;
    private final String procCategory;
    private final String decType;
    private final Clause ifClause;
    private final Clause thenClause;
    private final boolean thenNegated;
    private final java.util.List<String> errorCodes;
    private final boolean mergeable;
    private final boolean crossInstance;

    public EmitContext(String ruleId,
                       String procCategory,
                       String decType,
                       Clause ifClause,
                       Clause thenClause,
                       boolean thenNegated,
                       java.util.List<String> errorCodes,
                       boolean mergeable,
                       boolean crossInstance) {
        this.ruleId = ruleId == null ? "" : ruleId;
        this.procCategory = procCategory;
        this.decType = decType;
        this.ifClause = ifClause;
        this.thenClause = thenClause;
        this.thenNegated = thenNegated;
        this.errorCodes = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
        this.mergeable = mergeable;
        this.crossInstance = crossInstance;
    }

    public String ruleId() { return ruleId; }
    public String procCategory() { return procCategory; }
    public String decType() { return decType; }
    public Clause ifClause() { return ifClause; }
    public Clause thenClause() { return thenClause; }
    public boolean isThenNegated() { return thenNegated; }
    public java.util.List<String> errorCodes() { return errorCodes; }
    public boolean isMergeable() { return mergeable; }
    public boolean isCrossInstance() { return crossInstance; }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.0 (2025-10-30): Added mergeable/crossInstance projection.
 * 1.6.0 (2025-10-21): Introduced nested Clause; normalized ops.
 * --------------------------------------------------------------------
 */

/*
 * Module: rules-emitter
 * File: EmitterRegistry.java
 * @version: 1.7.0  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Chooses exactly one emitter by priority + canHandle(ctx).
 *
 * Change Highlights:
 * - 1.7.0: Registered SpSpEmitter (priority 90).
 */

public final class EmitterRegistry {
    private final java.util.List<Emitter> emitters = new java.util.ArrayList<>();

    public EmitterRegistry() {
        emitters.add(new SpSpEmitter());     // 90
        emitters.add(new RpPpEmitter());     // example 80
        emitters.add(new GenericEmitter());  // 10 fallback
        emitters.sort((a,b) -> Integer.compare(b.priority(), a.priority()));
    }

    public void emit(EmitContext ctx, DslrFileWriter dsl){
        for (Emitter e : emitters){
            if (e.canHandle(ctx)){ e.emit(ctx, dsl); return; }
        }
        new GenericEmitter().emit(ctx, dsl);
    }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.0 (2025-10-30): Added SpSpEmitter registration.
 * 1.6.0 (2025-10-21): Priority ordering enforced.
 * --------------------------------------------------------------------
 */


/*
 * Module: rules-emitter
 * File: GenericEmitter.java
 * @version: 1.7.1  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Single emitter handling all IF/THEN combos via a small combo-matrix.
 *
 * Change Highlights:
 * - 1.7.1: Replaced per-combo emitters with matrix-driven strategies.
 */

public final class GenericEmitter implements Emitter {

    // ---- Strategy plumbing ---------------------------------------------------
    @FunctionalInterface
    private interface EmissionStrategy {
        void emit(EmitContext ctx, DslrFileWriter dsl);
    }

    private record ComboKey(String ifSuffix, String thenSuffix) {
        static ComboKey of(String a, String b) { return new ComboKey(norm(a), norm(b)); }
        private static String norm(String s) {
            if (s == null) return "";
            String t = s.replace('\\','/').trim();
            // keep only last segment(s) we care about
            int i = t.lastIndexOf('/');
            return i >= 0 ? t.substring(i + 1) : t;
        }
    }

    private static final java.util.Map<ComboKey, EmissionStrategy> MATRIX = new java.util.HashMap<>();

    static {
        // RP → PP
        register("previousProcedure.code", "requestedProcedure.code", GenericEmitter::emitSameAnchorTwoDashLines);

        // SP → SP (cross-instance aware)
        register("specialProcedure.code", "specialProcedure.code", GenericEmitter::emitSameAnchorTwoDashLines);

        // AD → SP, etc. (add more as needed without new classes)
        register("additionalDocument.type", "specialProcedure.code", GenericEmitter::emitSameAnchorTwoDashLines);
        register("additionalDocument.code", "specialProcedure.code", GenericEmitter::emitSameAnchorTwoDashLines);

        // Fallback handled in emit()
    }

    private static void register(String ifSuffix, String thenSuffix, EmissionStrategy s) {
        MATRIX.put(ComboKey.of(ifSuffix, thenSuffix), s);
    }

    @Override public int priority() { return 50; } // single emitter, mid priority

    @Override
    public boolean canHandle(EmitContext ctx) {
        return MATRIX.containsKey(ComboKey.of(ctx.ifClause().path(), ctx.thenClause().path()));
    }

    @Override
    public void emit(EmitContext ctx, DslrFileWriter dsl) {
        EmissionStrategy s = MATRIX.get(ComboKey.of(ctx.ifClause().path(), ctx.thenClause().path()));
        if (s != null) {
            s.emit(ctx, dsl);
            return;
        }
        // Generic fallback if no exact combo registered:
        emitSameAnchorTwoDashLines(ctx, dsl);
    }

    // ---- Reusable strategy implementations ----------------------------------

    /**
     * One anchor line + two dash lines (IF group, THEN group).
     * Respects ctx.isThenNegated() for operator flipping, and joins error codes with pipes.
     * No dots appear in output; we print only the last segment.
     */
    private static void emitSameAnchorTwoDashLines(EmitContext ctx, DslrFileWriter dsl) {
        dsl.whenLine("GoodsItem exists");

        EmitContext.Clause ifc = ctx.ifClause();
        EmitContext.Clause thc = ctx.thenClause();

        for (String v : ifc.values()) {
            dsl.whenLine("- " + lastSeg(ifc.path()) + " " + op(ifc.op(), false) + " {" + v + "}");
        }

        boolean negate = ctx.isThenNegated();
        for (String v : thc.values()) {
            dsl.whenLine("- " + lastSeg(thc.path()) + " " + op(thc.op(), negate) + " {" + v + "}");
        }

        dsl.thenLine("Emit BR error: " + String.join("|", ctx.errorCodes()));
        // Note: if ctx.isCrossInstance() is true (e.g., SP→SP), runtime/engine enforces distinct instances.
    }

    // ---- small helpers -------------------------------------------------------

    private static String lastSeg(String path) {
        if (path == null || path.isBlank()) return "";
        String p = path.replace('\\','/').trim();
        int i = p.lastIndexOf('/');
        return (i >= 0) ? p.substring(i + 1) : p.substring(p.lastIndexOf('.') + 1); // tolerate dot/camel legacy
    }

    private static String op(String raw, boolean negate) {
        String sym = (raw == null || raw.isBlank()) ? "==" : raw;
        if (!negate) return sym;
        return switch (sym) { case "==" -> "!="; case "in" -> "not in"; default -> sym; };
    }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.1 (2025-10-30): Introduced combo matrix (RP→PP, SP→SP, AD→SP). Removed per-combo classes.
 * --------------------------------------------------------------------
 */



/*
 * Module: rules-emitter
 * File: EmitterRegistry.java
 * @version: 1.7.1  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Registry now registers only the GenericEmitter (matrix-driven).
 */

public final class EmitterRegistry {
    private final java.util.List<Emitter> emitters = new java.util.ArrayList<>();

    public EmitterRegistry() {
        emitters.add(new GenericEmitter());  // single point
        // any legacy emitters can remain, but GenericEmitter should win on canHandle()
        emitters.sort((a,b) -> Integer.compare(b.priority(), a.priority()));
    }

    public void emit(EmitContext ctx, DslrFileWriter dsl){
        for (Emitter e : emitters){
            if (e.canHandle(ctx)){ e.emit(ctx, dsl); return; }
        }
        // last resort: generic emitter still works as fallback
        new GenericEmitter().emit(ctx, dsl);
    }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.1 (2025-10-30): Reduced to GenericEmitter matrix model.
 * --------------------------------------------------------------------
 */
