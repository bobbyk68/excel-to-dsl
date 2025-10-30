// --------------------------- helpers (keep private) ---------------------------

// Example: turn your operator/kind enums into the DSL operator symbol
private String resolveOpForIf(Signature sig) {
    // Prefer your canonical mapping. This example assumes equals / in / exists patterns.
    return switch (sig.ifKind.op()) {
        case EQUALS -> "==";
        case IN     -> "in";
        case EXISTS -> "exists";
        default     -> sig.ifKind.op().symbol(); // or throw
    };
}

private String resolveOpForThen(Signature sig) {
    // Some teams use the same symbol set for THEN filters; adapt as needed.
    return switch (sig.thenKind.op()) {
        case EQUALS -> "==";
        case IN     -> "in";
        default     -> sig.thenKind.op().symbol();
    };
}

// Whether THEN is logically negated for this row (e.g., "must not contain ...")
private boolean isThenNegated(Signature sig) {
    return sig.thenEffect() != null && sig.thenEffect().isNegated();
}

// Safe accessors for parsed values (normalise to List<String>)
private java.util.List<String> valuesOf(ParsedClause clause) {
    return clause == null ? java.util.List.of() : clause.values();
}

private String pathOf(ParsedClause clause) {
    return clause == null ? "" : clause.path();
}


// --------------------------- main loop ---------------------------
public void collectAll(java.util.List<RuleRow> rows,
                       EmitterRegistry registry,
                       DslrFileWriter dsl) {

    for (RuleRow row : rows) {
        try {
            // (1) Parse → build a Signature (you already do this)
            //     Signature holds the "IF" and "THEN" shapes + effect (error codes, negation, etc.)
            Signature sig = signatureFor(row); // your existing method

            // (2) Build the two Clause objects (IF & THEN) the emitters will reason about
            GenericEmitter.Clause ifClause = new GenericEmitter.Clause(
                    pathOf(sig.ifClauseParsed()),           // e.g., "goodsItem.previousProcedure.code"
                    resolveOpForIf(sig),                    // e.g., "==", "in", "exists"
                    valuesOf(sig.ifClauseParsed())          // e.g., ["10D"] or []
            );

            GenericEmitter.Clause thenClause = new GenericEmitter.Clause(
                    pathOf(sig.thenClauseParsed()),         // e.g., "goodsItem.requestedProcedure.code"
                    resolveOpForThen(sig),                  // e.g., "==", "in"
                    valuesOf(sig.thenClauseParsed())        // e.g., ["1LP","1LR"]
            );

            // (3) Assemble the EmitContext (single place all emitters read from)
            EmitContext ctx = new EmitContext(
                    row.ruleId(),                           // keep your identifiers handy for messages
                    row.procCategory(),                     // if you propagate ProcCat/DecType
                    row.decType(),
                    ifClause,
                    thenClause,
                    isThenNegated(sig),                     // THEN negation flag
                    sig.thenEffect().errorCodes(),          // List<String> error codes (can repeat)
                    sig.meta()                              // anything else useful (anchor scope, etc.)
            );

            // (4) Hand off to the registry (it will select an emitter via canHandle(ctx))
            registry.emit(ctx, dsl);

        } catch (Exception e) {
            System.out.println("⚠ FAIL row " + row.ruleId() + " :: " + e.getMessage());
            // continue to next row
        }
    }
}


// --------------------------- example emitter usage ---------------------------
// inside some emitter:
public boolean canHandle(EmitContext ctx) {
    // Example: RP-PP shape: IF previousProcedure.code == X AND THEN requestedProcedure.code == Y
    return ctx.ifClause().path().endsWith("previousProcedure.code")
            && ctx.thenClause().path().endsWith("requestedProcedure.code")
            && !ctx.isThenNegated();
}

public void emit(EmitContext ctx, DslrFileWriter dsl) {
    // Existence line (anchor) – pick one side's domain as your scope
    dsl.whenLine("GoodsItem exists");

    // Dash lines (conditions)
    GenericEmitter.Clause ifc = ctx.ifClause();
    if (!ifc.values().isEmpty()) {
        for (String v : ifc.values()) {
            dsl.whenLine("- previousProcedure.code " + ifc.op() + " {" + v + "}");
        }
    }

    GenericEmitter.Clause thc = ctx.thenClause();
    if (!thc.values().isEmpty()) {
        for (String v : thc.values()) {
            dsl.whenLine("- requestedProcedure.code " + thc.op() + " {" + v + "}");
        }
    }

    // THEN side – format your error codes (allowing duplicates if you need pipes)
    String joined = String.join("|", ctx.errorCodes());
    if (ctx.isThenNegated()) {
        dsl.thenLine("Emit BR error (negated): " + joined);
    } else {
        dsl.thenLine("Emit BR error: " + joined);
    }
}


// --------------------------- EmitContext (shape) ---------------------------
public final class EmitContext {
    private final String ruleId;
    private final String procCategory;
    private final String decType;
    private final GenericEmitter.Clause ifClause;
    private final GenericEmitter.Clause thenClause;
    private final boolean thenNegated;
    private final java.util.List<String> errorCodes; // allow duplicates to preserve pipes
    private final java.util.Map<String, Object> meta;

    public EmitContext(String ruleId,
                       String procCategory,
                       String decType,
                       GenericEmitter.Clause ifClause,
                       GenericEmitter.Clause thenClause,
                       boolean thenNegated,
                       java.util.List<String> errorCodes,
                       java.util.Map<String, Object> meta) {
        this.ruleId = ruleId;
        this.procCategory = procCategory;
        this.decType = decType;
        this.ifClause = ifClause;
        this.thenClause = thenClause;
        this.thenNegated = thenNegated;
        this.errorCodes = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
        this.meta = meta == null ? java.util.Map.of() : java.util.Map.copyOf(meta);
    }

    public String ruleId() { return ruleId; }
    public String procCategory() { return procCategory; }
    public String decType() { return decType; }
    public GenericEmitter.Clause ifClause() { return ifClause; }
    public GenericEmitter.Clause thenClause() { return thenClause; }
    public boolean isThenNegated() { return thenNegated; }
    public java.util.List<String> errorCodes() { return errorCodes; }
    public java.util.Map<String,Object> meta() { return meta; }
}
