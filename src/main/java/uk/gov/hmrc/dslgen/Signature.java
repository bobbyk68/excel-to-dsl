/*
 * Module: rules-emitter
 * File: WhenPatternMatcher.collectAll (method)
 * @version: 1.7.0  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Row loop → Signature → EmitContext → Registry.emit
 *
 * Change Highlights:
 * - 1.7.0: SP-SP detection sets crossInstance; propagate negateThen.
 */

public void collectAll(java.util.List<RuleRow> rows,
                       EmitterRegistry registry,
                       DslrFileWriter dsl) {
    for (RuleRow row : rows) {
        String ifPath   = deriveCanonicalIfPath(row);
        String thenPath = deriveCanonicalThenPath(row);
        String ifOp     = mapIfOperator(row);
        String thenOp   = mapThenOperator(row);
        java.util.List<String> ifVals   = parseIfValues(row);
        java.util.List<String> thenVals = parseThenValues(row);
        java.util.List<String> errs     = parseErrorCodes(row);
        boolean negThen = detectNegation(row);

        boolean isSpSp = ifPath.endsWith("specialProcedure.code")
                && thenPath.endsWith("specialProcedure.code");

        Signature sig = new SignatureBuilder()
                .ifPath(ifPath).ifOp(ifOp).ifValues(ifVals)
                .thenPath(thenPath).thenOp(thenOp).thenValues(thenVals)
                .mergeable(false)
                .crossInstance(isSpSp)
                .negateThen((negThen))
                .errorCodes(errs)
                .procCategory(row.procCategory())
                .decType(row.decType())
                .build();

        EmitContext.Clause ifC   = new EmitContext.Clause(sig.ifPath(),   sig.ifOpSymbol(),   sig.ifValues());
        EmitContext.Clause thenC = new EmitContext.Clause(sig.thenPath(), sig.thenOpSymbol(), sig.thenValues());

        EmitContext ctx = new EmitContext(
                row.ruleId(),
                sig.procCategory() != null ? sig.procCategory() : row.procCategory(),
                sig.decType()      != null ? sig.decType()      : row.decType(),
                ifC, thenC,
                sig.negateThen(),
                sig.errorCodes(),
                sig.mergeable(),
                sig.crossInstance()
        );

        registry.emit(ctx, dsl);
    }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.0 (2025-10-30): SP-SP detection (crossInstance=true), propagate negateThen.
 * --------------------------------------------------------------------
 */
