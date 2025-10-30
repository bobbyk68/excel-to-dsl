/*
 * Module: rules-emitter
 * File: WhenPatternMatcher.collectAll (method)
 * @version: 1.7.2  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Build EmitContext directly from RuleRow fields. No helpers.
 */

public void collectAll(java.util.List<RuleRow> rows,
                       EmitterRegistry registry,
                       DslrFileWriter dsl) {

    for (RuleRow row : rows) {

        // 1) Build Signature straight from row (no helpers)
        Signature sig = new Signature(
                row.ifPath(),            // e.g. "goodsItem/previousProcedure/code" (canonical)
                row.ifOpSymbol(),        // e.g. "==", "in", "exists"
                row.ifValues(),          // List<String>

                row.thenPath(),          // e.g. "goodsItem/requestedProcedure/code"
                row.thenOpSymbol(),      // e.g. "=="
                row.thenValues(),        // List<String>

                row.mergeable(),         // boolean
                row.crossInstance(),     // boolean (true for SP→SP)
                row.negateThen(),        // boolean ("must not ..." etc.)

                row.errorCodes(),        // List<String> (keep dupes for pipe-join)
                row.procCategory(),      // String or null
                row.decType()            // String or null
        );

        // 2) Project Signature → EmitContext
        EmitContext.Clause ifC   = new EmitContext.Clause(sig.ifPath(),   sig.ifOpSymbol(),   sig.ifValues());
        EmitContext.Clause thenC = new EmitContext.Clause(sig.thenPath(), sig.thenOpSymbol(), sig.thenValues());

        EmitContext ctx = new EmitContext(
                row.ruleId(),
                sig.procCategory() != null ? sig.procCategory() : row.procCategory(),
                sig.decType()      != null ? sig.decType()      : row.decType(),
                ifC,
                thenC,
                sig.negateThen(),
                sig.errorCodes(),
                sig.mergeable(),
                sig.crossInstance()
        );

        // 3) Emit
        registry.emit(ctx, dsl);
    }
}

/*
 * -------- Mini Changelog -------------------------------------------
 * 1.7.2 (2025-10-30): Direct-from-Row variant (no derive* methods).
 * --------------------------------------------------------------------
 */

/*
 * Module: rules-emitter
 * File: WhenPatternMatcher.collectAll (method)
 * @version: 1.7.2  (release: 2025-10-30)
 * @since:   1.0.0
 * Owner:    BK/RK
 *
 * Summary:
 * Build EmitContext directly from Row.left()/Row.right() fields. No helpers.
 */

public void collectAll(java.util.List<RuleRow> rows,
                       EmitterRegistry registry,
                       DslrFileWriter dsl) {

    for (RuleRow row : rows) {

        // Assumes these exist:
        // row.left().path(), row.left().op(), row.left().values()
        // row.right().path(), row.right().op(), row.right().values()
        // row.flags().mergeable(), row.flags().crossInstance(), row.flags().negateThen()
        // row.errorCodes(), row.procCategory(), row.decType()
        // row.ruleId()

        Signature sig = new Signature(
                row.left().path(),
                row.left().op(),
                row.left().values(),

                row.right().path(),
                row.right().op(),
                row.right().values(),

                row.flags().mergeable(),
                row.flags().crossInstance(),
                row.flags().negateThen(),

                row.errorCodes(),
                row.procCategory(),
                row.decType()
        );

        EmitContext.Clause ifC   = new EmitContext.Clause(sig.ifPath(),   sig.ifOpSymbol(),   sig.ifValues());
        EmitContext.Clause thenC = new EmitContext.Clause(sig.thenPath(), sig.thenOpSymbol(), sig.thenValues());

        EmitContext ctx = new EmitContext(
                row.ruleId(),
                sig.procCategory() != null ? sig.procCategory() : row.procCategory(),
                sig.decType()      != null ? sig.decType()      : row.decType(),
                ifC,
                thenC,
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
 * 1.7.2 (2025-10-30): Direct-from-left/right variant (no helpers).
 * --------------------------------------------------------------------
 */
