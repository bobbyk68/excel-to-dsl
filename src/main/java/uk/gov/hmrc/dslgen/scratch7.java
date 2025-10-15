public void collectAll(List<RuleRow> rows, EmitterRegistry registry, Path outDir) {
    DslrFileWriter dsl = new DslrFileWriter();

    for (RuleRow row : rows) {
        // 1) Adapt your existing English to AtomicHits (no renames)
        var leftHit  = RuleRowAdapter.left(row);
        var rightHit = RuleRowAdapter.right(row);
        var thenHit  = RuleRowAdapter.then(row); // may be null

        // 2) Build context (derives anchor + thenTarget; no hard-coded strings)
        EmitContext ctx = EmitContextFactory.fromHits(leftHit, rightHit, thenHit);

        // 3) Emit
        dsl.reset();
        boolean handled = registry.dispatch(ctx, dsl);
        String body = handled ? dsl.getText() : "[no emitter handled]";

        // 4) Wrap with your rule header/footer, then persist
        String fullRule = wrapWithHeader(row, body);   // your existing envelope method
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve(row.id() + ".dslr"), fullRule);
    }
}


RuleCollector collector = new RuleCollector(registry);
collector.collectAll(ruleRows, (row, fullRuleText) -> {
Path out = Paths.get("build/dsl-out");
    try {
            Files.createDirectories(out);
        Files.writeString(out.resolve(row.id() + ".dslr"), fullRuleText);
        } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
            });

public final class DslGenerationService {

    private final EmitterRegistry registry;

    public DslGenerationService() {
        this.registry = buildRegistry();
    }

    private EmitterRegistry buildRegistry() {
        EmitterRegistry reg = new EmitterRegistry();
        reg.register(new SpSpEmitter());
        reg.register(new RpPpEmitter());
        reg.register(new SpAiEmitter());
        reg.register(new SpAdEmitter());
        reg.register(new RpSpEmitter());
        reg.register(new DefaultFallbackEmitter());
        reg.sortByPriorityDesc();
        return reg;
    }

    public void run(List<RuleRow> rows, Path outDir) {
        collectAll(rows, registry, outDir); // Path A (use your existing collectAll)
        // or:
        // new RuleCollector(registry).collectAll(rows, (row, txt) -> write to outDir…); // Path B
    }
}
