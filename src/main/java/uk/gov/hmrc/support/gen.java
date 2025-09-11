package uk.gov.hmrc.support;

public class gen {
    public void gen() {
        // inside your per-row loop, right after you obtain the Atomic hits for col1/col2
        DslFileWriter dslWriter = /* get singleton or pass into Generator */;

// for each atomic (col1/col2):
        for (AtomicHit hit : hits) {
            // 1) LHS of .dsl (mapping) is the JSON dsl phrase verbatim
            String lhsWhen = "[when] " + hit.dsl(); // e.g. "at least one {path} equals {value}"

            // 2) RHS of .dsl (mapping) is DRL condition generated from quantifier+operator
            String rhsWhen = DslMappingBuilder.buildWhenRhs(hit); // e.g. "exists GoodItem( code == {value} )"

            dslWriter.appendWhen(lhsWhen, rhsWhen);

            // OPTIONAL: also create a [then] mapping for this rule (if needed)
            // String lhsThen = "[then] raise {severity} \"{message}\" at {path}";
            // String rhsThen = DslMappingBuilder.buildThenRhs(); // standard violation action
            // dslWriter.appendThen(lhsThen, rhsThen);
        }

// when done (per file or at end):
// dslWriter.writeTo(Paths.get("rules.dsl"));

    }
}
