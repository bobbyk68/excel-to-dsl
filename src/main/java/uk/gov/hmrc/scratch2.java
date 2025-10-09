// ─────────────────────────────────────────────────────────────────────────────
// Emitter.java  (your preferred interface)
// ─────────────────────────────────────────────────────────────────────────────
public interface Emitter {
    int priority();                       // Higher wins when multiple canHandle(...) are true
    boolean canHandle(EmitContext ctx);   // Decide if this emitter applies to the row
    void emit(EmitContext ctx, DslFileWriter dsl);
}

// ─────────────────────────────────────────────────────────────────────────────
// EmitContext.java  (carry BOTH hits + the source row)
// ─────────────────────────────────────────────────────────────────────────────
public final class EmitContext {
    private final RuleRow row;        // your existing parsed row (text, ids, etc.)
    private final AtomicHit ifHit;    // parsed payload from Column B (IF)
    private final AtomicHit thenHit;  // parsed payload from Column C (THEN)

    public EmitContext(RuleRow row, AtomicHit ifHit, AtomicHit thenHit) {
        this.row = row;
        this.ifHit = ifHit;
        this.thenHit = thenHit;
    }

    public RuleRow row() { return row; }
    public AtomicHit ifHit() { return ifHit; }
    public AtomicHit thenHit() { return thenHit; }

    // Convenience accessors (optional)
    public String ifSingle() { return ifHit != null ? String.valueOf(ifHit.value()) : null; }
    public java.util.List<String> thenList() {
        return thenHit != null && thenHit.values() != null
                ? thenHit.values().stream().map(String::valueOf).toList()
                : java.util.List.of();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DslFileWriter.java  (adapter to your existing DslWriter; keep simple)
// ─────────────────────────────────────────────────────────────────────────────
public interface DslFileWriter {
    void whenLine(String text);
    void thenLine(String text);
}

// ─────────────────────────────────────────────────────────────────────────────
// GoodsItemSpecialProcedureEmitter.java  (Row 2: uses BOTH ifHit and thenHit)
// Emits EXACT DSL shape you showed:
//   Goods item with special procedure exists
//       - with code equals "C601"
//   Matching goods item with previous procedure exists
//       - with code not in "00","21","51","53","54","71","78"
//   Emit BR675 validation error for requested and previous procedure
// ─────────────────────────────────────────────────────────────────────────────
public final class GoodsItemSpecialProcedureEmitter implements Emitter {

    @Override
    public int priority() {
        return 100; // specific > generic
    }

    @Override
    public boolean canHandle(EmitContext ctx) {
        // Keep this narrow to avoid false positives.
        // We rely on the parsed row text you already have.
        String ifText   = ctx.row().ifText()   == null ? "" : ctx.row().ifText().toLowerCase();
        String thenText = ctx.row().thenText() == null ? "" : ctx.row().thenText().toLowerCase();

        boolean matchesIf   = ifText.contains("goodsitem.specialprocedures.code")
                || ifText.contains("goods item with special procedure");
        boolean matchesThen = thenText.contains("goodsitem.previousprocedure.code")
                || thenText.contains("matching goods item with previous procedure");

        // Both clauses must be present for this emitter
        return matchesIf && matchesThen;
    }

    @Override
    public void emit(EmitContext ctx, DslFileWriter dsl) {
        // 1) IF value (single) — e.g., "C601"
        String sp = ctx.ifSingle(); // via convenience accessor; equals hit.value()
        if (sp == null) sp = "";    // defensive — should not happen for this emitter

        // 2) THEN list (we FLIP it to "not in ...")
        java.util.List<String> allowed = ctx.thenList(); // e.g., ["00","21","51","53","54","71","78"]
        String joined = allowed.isEmpty()
                ? "\"\"" // empty guard; will still render valid DSL
                : "\"" + String.join("\",\"", allowed) + "\"";

        // ----- Block 1: special procedure (positive IF) -----
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("    - with code equals \"" + sp + "\"");

        // ----- Block 2: previous procedure (FLIPPED) -----
        dsl.whenLine("Matching goods item with previous procedure exists");
        dsl.whenLine("    - with code not in " + joined);

        // ----- THEN action / message (keep your literal) -----
        dsl.thenLine("Emit BR675 validation error for requested and previous procedure");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// collectAll(...) usage  (build context with BOTH hits)
// Plug into your existing flow where you already have ifHit and thenHit.
// ─────────────────────────────────────────────────────────────────────────────
public void collectAll(RuleRow row, AtomicHit ifHit, AtomicHit thenHit, DslFileWriter dsl) {

    EmitContext ctx = new EmitContext(row, ifHit, thenHit);

    java.util.List<Emitter> emitters = java.util.List.of(
            new GoodsItemSpecialProcedureEmitter()
            // add more as you migrate: each can read ctx.ifHit() and ctx.thenHit()
    );

    emitters.stream()
            .filter(e -> e.canHandle(ctx))
            .sorted(java.util.Comparator.comparingInt(Emitter::priority).reversed())
            .findFirst()
            .ifPresent(e -> e.emit(ctx, dsl));

    // If none matched, your legacy path can still run below (optional):
    // if (!anyMatched) { legacyEmit(row, ifHit, thenHit, dsl); }
}
