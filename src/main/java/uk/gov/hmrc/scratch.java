// ─────────────────────────────────────────────────────────────────────────────
// Emitter.java
// ─────────────────────────────────────────────────────────────────────────────
public interface Emitter {

    /** Higher = takes precedence if multiple canHandle(...) return true. */
    int priority();

    /** True if this emitter can handle the current context (row + hit). */
    boolean canHandle(EmitContext ctx);

    /** Writes DSL lines using the given writer. */
    void emit(EmitContext ctx, DslFileWriter dsl);
}

// ─────────────────────────────────────────────────────────────────────────────
// EmitContext.java  (simple carrier for row + hit info)
// ─────────────────────────────────────────────────────────────────────────────
public final class EmitContext {
    private final RuleRow row;
    private final AtomicHit hit;

    public EmitContext(RuleRow row, AtomicHit hit) {
        this.row = row;
        this.hit = hit;
    }

    public RuleRow row() { return row; }
    public AtomicHit hit() { return hit; }
}

// ─────────────────────────────────────────────────────────────────────────────
// DslFileWriter.java  (interface version of your existing DslWriter)
// ─────────────────────────────────────────────────────────────────────────────
public interface DslFileWriter {
    void whenLine(String text);
    void thenLine(String text);
}

// ─────────────────────────────────────────────────────────────────────────────
// GoodsItemSpecialProcedureEmitter.java  (Row 2 implementation)
// ─────────────────────────────────────────────────────────────────────────────
public final class GoodsItemSpecialProcedureEmitter implements Emitter {

    @Override
    public int priority() {
        return 100; // higher than any generic emitter
    }

    @Override
    public boolean canHandle(EmitContext ctx) {
        String ifText   = ctx.row().ifText().toLowerCase();
        String thenText = ctx.row().thenText().toLowerCase();

        return ifText.contains("goodsitem.specialprocedures.code")
                && thenText.contains("previousprocedure.code");
    }

    @Override
    public void emit(EmitContext ctx, DslFileWriter dsl) {
        String value = ctx.hit().value(); // e.g. "C601"

        // ----- first block: special procedure exists -----
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("    - with code equals \"" + value + "\"");

        // ----- second block: previous procedure (flipped condition) -----
        String[] disallowed = { "00","21","51","53","54","71","78" };
        String joined = "\"" + String.join("\",\"", disallowed) + "\"";

        dsl.whenLine("Matching goods item with previous procedure exists");
        dsl.whenLine("    - with code not in " + joined);

        // ----- then block: validation message -----
        dsl.thenLine("Emit BR675 validation error for requested and previous procedure");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Example usage inside collectAll(...) or similar
// ─────────────────────────────────────────────────────────────────────────────
public void collectAll(RuleRow row, AtomicHit hit, DslFileWriter dsl) {

    // Build the runtime context for this row
    EmitContext ctx = new EmitContext(row, hit);

    // Register (or inject) available emitters
    java.util.List<Emitter> emitters = java.util.List.of(
            new GoodsItemSpecialProcedureEmitter()
            // add others as you migrate more rows
    );

    // Choose the first emitter that can handle this row, by highest priority
    emitters.stream()
            .filter(e -> e.canHandle(ctx))
            .sorted(java.util.Comparator.comparingInt(Emitter::priority).reversed())
            .findFirst()
            .ifPresent(e -> e.emit(ctx, dsl));
}
