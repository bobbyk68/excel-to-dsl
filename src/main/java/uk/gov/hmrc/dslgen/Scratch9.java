public record RuleMeta(String ruleName,
                       String errorCode,
                       java.util.List<String> declarationTypes,
                       java.util.List<String> procedureCategories) {
    public static RuleMeta of(RuleRow r) {
        return new RuleMeta(r.id(), r.errorCode(), r.declarationType(), r.procedureCategory());
    }
}


// in DslrFileWriter (or your SimpleDslFileWriter)
public void beginRule(RuleMeta m) {
    buf.append("rule \"").append(m.ruleName()).append("\"\n");
    buf.append("@ErrorCode(\"").append(m.errorCode()).append("\")\n");
    buf.append("@declarationType(\"").append(String.join(",", m.declarationTypes())).append("\")\n");
    buf.append("@procedureCategory(\"").append(String.join(",", m.procedureCategories())).append("\")\n");
}
public void endRule() { buf.append("end\n"); }


RuleMeta meta = RuleMeta.of(row);
DslrFileWriter dsl = new DslrFileWriter();
dsl.beginRule(meta);                    // header here (once)
registry.dispatch(ctx, dsl);            // emitters only add WHEN/THEN lines
dsl.endRule();
