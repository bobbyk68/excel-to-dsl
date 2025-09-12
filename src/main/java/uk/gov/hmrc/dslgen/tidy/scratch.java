package uk.gov.hmrc.dslgen.tidy;

private String deriveOperatorToken(String text) {
    String s = (text == null) ? "" : text.toLowerCase();
    if (s.matches(".*\\b(only one|exactly one|one and only one)\\b.*")) return "ONE_OF";
    if (s.matches(".*\\b(none|no|must not)\\b.*"))                       return "NONE";
    if (s.matches(".*\\b(any of)\\b.*"))                                 return "ANY_OF";
    if (s.matches(".*\\b(all of|every|all must)\\b.*"))                  return "ALL_OF";
    if (s.matches(".*\\b(at least one|exists|there is|present)\\b.*"))   return "EXISTS";
    return "ALL_OF"; // sensible default (AND)
}

private ConstraintCase resolveCase(String token) {
    if (token == null) return ConstraintCase.ALL_OF;
    switch (token.trim().toUpperCase()) {
        case "ONE": case "ONLY_ONE": case "ONE_OF":  return ConstraintCase.ONE_OF;
        case "NONE": case "NOT":                     return ConstraintCase.NONE;
        case "ANY":  case "ANY_OF":                  return ConstraintCase.ANY_OF;
        case "ALL":  case "ALL_OF":                  return ConstraintCase.ALL_OF;
        case "EXISTS": case "AT_LEAST_ONE":          return ConstraintCase.EXISTS;
        case "NONE_OF":                              return ConstraintCase.NONE_OF;
        default:                                     return ConstraintCase.ALL_OF;
    }
}

String leftLiteral = row.ifCondition();   // you already have this
String token = deriveOperatorToken(leftLiteral);

// Stash for pass 2
rowsHits.add(new RowHits(
        row.businessRuleId(),
token,                 // ← derived here
        List.of(left, right)
));

ConstraintCase kase = resolveCase(rh.operatorToken);

for (AtomicHit hit : rh.hits) {
List<String> lines = twoPhaseComposer.composeLeaf(
        kase,
        hit.dsl(),                         // template (may contain " - ")
        (hit.groups()!=null && !hit.groups().isEmpty()) ? hit.groups().get(0) : "" // value
);
    for (String line : lines) out.append(line).append('\n');
}
        out.append('\n');
