public record Signature(
        String ifPath,                 // e.g. "goodsItem.previousProcedure.code"
        String ifOpSymbol,             // e.g. "==", "in", "exists"
        java.util.List<String> ifValues,

        String thenPath,               // e.g. "goodsItem.requestedProcedure.code"
        String thenOpSymbol,           // e.g. "==", "in"
        java.util.List<String> thenValues,

        boolean thenNegated,           // true if THEN is logically negated (e.g., "must NOT contain ...")
        java.util.List<String> errorCodes, // keep duplicates if you need pipes "A|A|B"

        String procCategory,           // optional pass-through; can be null
        String decType                 // optional pass-through; can be null
) {
    public Signature {
        ifPath       = ifPath == null ? "" : ifPath;
        ifOpSymbol   = ifOpSymbol == null ? "" : ifOpSymbol;
        ifValues     = ifValues == null ? java.util.List.of() : java.util.List.copyOf(ifValues);

        thenPath     = thenPath == null ? "" : thenPath;
        thenOpSymbol = thenOpSymbol == null ? "" : thenOpSymbol;
        thenValues   = thenValues == null ? java.util.List.of() : java.util.List.copyOf(thenValues);

        errorCodes   = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
        // procCategory and decType may be null; EmitContext can fall back to row values if needed.
    }

    // Convenience queries most emitters/registries end up using
    public boolean ifIsExists()   { return "exists".equalsIgnoreCase(ifOpSymbol); }
    public boolean thenIsExists() { return "exists".equalsIgnoreCase(thenOpSymbol); }
    public boolean ifHasNoValues(){ return ifValues.isEmpty(); }
    public boolean thenHasNoValues(){ return thenValues.isEmpty(); }

    // Fluent “with…” helpers (handy if you tweak after parsing)
    public Signature withProcCategory(String pc) { return new Signature(
            ifPath, ifOpSymbol, ifValues,
            thenPath, thenOpSymbol, thenValues,
            thenNegated, errorCodes,
            pc, decType
    ); }

    public Signature withDecType(String dt) { return new Signature(
            ifPath, ifOpSymbol, ifValues,
            thenPath, thenOpSymbol, thenValues,
            thenNegated, errorCodes,
            procCategory, dt
    ); }

    public Signature withErrorCodes(java.util.List<String> codes) { return new Signature(
            ifPath, ifOpSymbol, ifValues,
            thenPath, thenOpSymbol, thenValues,
            thenNegated, codes,
            procCategory, decType
    ); }

    // Static factories to keep call sites clean
    public static Signature of(String ifPath, String ifOp, java.util.List<String> ifVals,
                               String thenPath, String thenOp, java.util.List<String> thenVals,
                               boolean thenNegated, java.util.List<String> errorCodes) {
        return new Signature(ifPath, ifOp, ifVals, thenPath, thenOp, thenVals, thenNegated, errorCodes, null, null);
    }

    public static Signature ofWithContext(String ifPath, String ifOp, java.util.List<String> ifVals,
                                          String thenPath, String thenOp, java.util.List<String> thenVals,
                                          boolean thenNegated, java.util.List<String> errorCodes,
                                          String procCategory, String decType) {
        return new Signature(ifPath, ifOp, ifVals, thenPath, thenOp, thenVals, thenNegated, errorCodes, procCategory, decType);
    }
}


// after you’ve parsed the Excel row into canonical path/op/values:
Signature sig = Signature.ofWithContext(
        canonicalIfPath,   mappedIfOpSymbol,   parsedIfValues,
        canonicalThenPath, mappedThenOpSymbol, parsedThenValues,
        parsedThenNegated, parsedErrorCodes,
        /* optional: */ row.procCategory(), row.decType()
);

// Build EmitContext using fields directly from Signature
Clause ifClause  = new Clause(sig.ifPath(),  sig.ifOpSymbol(),  sig.ifValues());
Clause thenClause= new Clause(sig.thenPath(),sig.thenOpSymbol(),sig.thenValues());

EmitContext ctx = new EmitContext(
        row.ruleId(),
        sig.procCategory() != null ? sig.procCategory() : row.procCategory(),
        sig.decType()      != null ? sig.decType()      : row.decType(),
        ifClause,
        thenClause,
        sig.thenNegated(),
        sig.errorCodes()
);

registry.emit(ctx, dsl);



public final class EmitContext {

    // -------- nested small value object --------
    public static final class Clause {
        private final String path;                 // e.g. "goodsItem.previousProcedure.code"
        private final String op;                   // e.g. "==", "in", "exists"
        private final java.util.List<String> values;

        public Clause(String path, String op, java.util.List<String> values) {
            this.path   = path   == null ? "" : path;
            this.op     = op     == null ? "" : op;
            this.values = values == null ? java.util.List.of() : java.util.List.copyOf(values);
        }
        public String path() { return path; }
        public String op() { return op; }
        public java.util.List<String> values() { return values; }

        public boolean isExists()    { return "exists".equalsIgnoreCase(op); }
        public boolean hasNoValues() { return values.isEmpty(); }
    }
    // -------------------------------------------

    private final String ruleId;
    private final String procCategory;
    private final String decType;
    private final Clause ifClause;
    private final Clause thenClause;
    private final boolean thenNegated;
    private final java.util.List<String> errorCodes;

    public EmitContext(String ruleId,
                       String procCategory,
                       String decType,
                       Clause ifClause,
                       Clause thenClause,
                       boolean thenNegated,
                       java.util.List<String> errorCodes) {

        this.ruleId = ruleId == null ? "" : ruleId;
        this.procCategory = procCategory;
        this.decType = decType;
        this.ifClause = ifClause;
        this.thenClause = thenClause;
        this.thenNegated = thenNegated;
        this.errorCodes = errorCodes == null ? java.util.List.of() : java.util.List.copyOf(errorCodes);
    }

    public String ruleId() { return ruleId; }
    public String procCategory() { return procCategory; }
    public String decType() { return decType; }
    public Clause ifClause() { return ifClause; }
    public Clause thenClause() { return thenClause; }
    public boolean isThenNegated() { return thenNegated; }
    public java.util.List<String> errorCodes() { return errorCodes; }
}


public record Signature(
        String ifPath, String ifOpSymbol, java.util.List<String> ifValues,
        String thenPath, String thenOpSymbol, java.util.List<String> thenValues,
        boolean thenNegated, java.util.List<String> errorCodes,
        String procCategory, String decType
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
}


public void collectAll(java.util.List<RuleRow> rows,
                       EmitterRegistry registry,
                       DslrFileWriter dsl) {

    for (RuleRow row : rows) {
        try {
            // Parse the row into canonical parts (use whatever you already have)
            Signature sig = parseToSignature(row); // your method

            EmitContext.Clause ifC = new EmitContext.Clause(
                    sig.ifPath(), sig.ifOpSymbol(), sig.ifValues()
            );

            EmitContext.Clause thenC = new EmitContext.Clause(
                    sig.thenPath(), sig.thenOpSymbol(), sig.thenValues()
            );

            EmitContext ctx = new EmitContext(
                    row.ruleId(),
                    sig.procCategory() != null ? sig.procCategory() : row.procCategory(),
                    sig.decType()      != null ? sig.decType()      : row.decType(),
                    ifC,
                    thenC,
                    sig.thenNegated(),
                    sig.errorCodes()   // allow duplicates if you want pipe-joining "A|A|B"
            );

            registry.emit(ctx, dsl);

        } catch (Exception e) {
            System.out.println("FAIL row " + row.ruleId() + " :: " + e.getMessage());
        }
    }
}


public boolean canHandle(EmitContext ctx) {
    return ctx.ifClause().path().endsWith("previousProcedure.code")
            && ctx.thenClause().path().endsWith("requestedProcedure.code")
            && !ctx.isThenNegated();
}

public void emit(EmitContext ctx, DslrFileWriter dsl) {
    dsl.whenLine("GoodsItem exists");

    EmitContext.Clause ifc = ctx.ifClause();
    for (String v : ifc.values()) {
        dsl.whenLine("- previousProcedure.code " + ifc.op() + " {" + v + "}");
    }

    EmitContext.Clause thc = ctx.thenClause();
    for (String v : thc.values()) {
        dsl.whenLine("- requestedProcedure.code " + thc.op() + " {" + v + "}");
    }

    dsl.thenLine("Emit BR error: " + String.join("|", ctx.errorCodes()));
}
