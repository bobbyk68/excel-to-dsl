package uk.gov.hmrc.dslgen;

import java.util.List;
import uk.gov.hmrc.dslgen.emit.adapter.AtomicHitAdapter;
import uk.gov.hmrc.dslgen.emit.parse.RuleRow;
import uk.gov.hmrc.dslgen.emit.parse.RuleRowAdapter;

public final class RuleCollector {

    private final EmitterRegistry registry;

    public RuleCollector(EmitterRegistry registry) {
        this.registry = registry;
    }

    public void collectAll(List<RuleRow> rows, java.util.function.BiConsumer<RuleRow,String> sink) {
        DslrFileWriter dsl = new DslrFileWriter();

        for (RuleRow row : rows) {
            // 1) Build hits from RuleRow (no assumptions beyond adapter)
            AtomicHitAdapter.AtomicHit leftHit  = RuleRowAdapter.left(row);
            AtomicHitAdapter.AtomicHit rightHit = RuleRowAdapter.right(row);
            AtomicHitAdapter.AtomicHit thenHit  = RuleRowAdapter.then(row);

            // 2) Build context (derived anchor + derived thenTarget)
            EmitContext ctx = EmitContextFactory.fromHits(leftHit, rightHit, thenHit);

            // 3) Emit
            dsl.reset();
            boolean handled = registry.dispatch(ctx, dsl);

            // 4) Hand back the DSLR text (you can wrap here with rule headers/footers)
            String body = handled ? dsl.getText() : "[no emitter handled]";
            sink.accept(row, body);
        }
    }
}

EmitterRegistry reg = new EmitterRegistry();
// register your 5–7 emitters here (hard-coded or phrasebook versions)…
reg.sortByPriorityDesc();

RuleCollector collector = new RuleCollector(reg);
collector.collectAll(myRuleRows, (row, dslrBody) -> {
        // wrap with rule metadata and write to file, DB, whatever
        // e.g., writeRule(row.ruleId(), row.errorCode(), dslrBody);
        });
