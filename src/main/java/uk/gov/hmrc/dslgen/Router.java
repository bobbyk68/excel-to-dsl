// Split "path <op phrase> <values?>"
private DslrGen.ParsedClause parseClause(String raw) {
    String s = raw.trim();

    // 1) path = leading token until first operator keyword
    // We’ll search for the longest matching operator phrase we support.
    String[] OPS = {
            "not exists", "does not exist", "is not", "must not equal", "not equals", "not in", "must not be one of",
            "exists", "is present", "equals", "equal to", "is", "in", "is one of", "must be one of"
    };

    int bestPos = -1, bestLen = -1;
    String bestOp = null;
    for (String op : OPS) {
        int pos = indexOfIgnoreCase(s, " " + op + " ");           // flanked by spaces
        if (pos < 0 && (op.equals("exists") || op.equals("not exists") || op.equals("does not exist") || op.equals("is"))) {
            // allow terminal ops like "... exists"
            pos = endsWithIgnoreCase(s, " " + op) ? s.toLowerCase().lastIndexOf(" " + op) : -1;
        }
        if (pos >= 0 && (op.length() > bestLen)) { bestPos = pos; bestLen = op.length(); bestOp = op; }
    }
    if (bestOp == null) throw new IllegalArgumentException("Unsupported operator in: " + raw);

    String path = s.substring(0, bestPos).trim();
    String tail = s.substring(bestPos + 1).trim(); // drop leading space
    // tail starts with bestOp
    String opToken = bestOp;
    String valuesPart = tail.substring(bestOp.length()).trim();   // may be empty for exists/not exists

    // 2) values: quoted scalar or bracketed list
    java.util.List<String> values = java.util.List.of();
    if (!valuesPart.isEmpty()) {
        if (valuesPart.startsWith("[")) {
            values = parseList(valuesPart); // ["A","B"] -> List.of("A","B")
        } else if (valuesPart.startsWith("\"")) {
            values = java.util.List.of(unquote(valuesPart));
        } else {
            // Some sheets don’t quote singletons; accept raw token
            values = java.util.List.of(valuesPart.replaceAll("\\s+$", ""));
        }
    }

    return new DslrGen.ParsedClause(path, opToken, values);
}

private java.util.List<String> parseList(String part) {
    // Expect form: ["A","B","C"] (tolerate spaces)
    String inner = part.trim();
    if (!inner.startsWith("[") || !inner.endsWith("]"))
        throw new IllegalArgumentException("Bad list syntax: " + part);
    inner = inner.substring(1, inner.length()-1).trim();
    if (inner.isEmpty()) return java.util.List.of();
    java.util.ArrayList<String> out = new java.util.ArrayList<>();
    int i = 0;
    while (i < inner.length()) {
        // skip commas/spaces
        while (i < inner.length() && (inner.charAt(i) == ',' || Character.isWhitespace(inner.charAt(i)))) i++;
        if (i >= inner.length()) break;
        if (inner.charAt(i) == '"') {
            int j = i + 1;
            StringBuilder sb = new StringBuilder();
            while (j < inner.length() && inner.charAt(j) != '"') { sb.append(inner.charAt(j++)); }
            if (j >= inner.length()) throw new IllegalArgumentException("Unclosed quote in: " + part);
            out.add(sb.toString());
            i = j + 1;
        } else {
            // unquoted token until comma
            int j = i;
            while (j < inner.length() && inner.charAt(j) != ',') j++;
            out.add(inner.substring(i, j).trim());
            i = j;
        }
    }
    return out;
}

private String unquote(String s) {
    s = s.trim();
    if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) return s.substring(1, s.length()-1);
    return s;
}

private int indexOfIgnoreCase(String haystack, String needle) {
    return haystack.toLowerCase(java.util.Locale.ROOT).indexOf(needle.toLowerCase(java.util.Locale.ROOT));
}
private boolean endsWithIgnoreCase(String s, String suffix) {
    return s.toLowerCase(java.util.Locale.ROOT).endsWith(suffix.toLowerCase(java.util.Locale.ROOT));
}



for (RuleRow row : rows) {
// Your splitting → one atomic pair per logical rule
// (If you previously created separate IF-only / THEN-only hits, stop doing that—merge to one pair.)

String ifText   = row.ifText();    // e.g., from CSV column
String thenText = row.thenText();

var ifClause   = parseClause(ifText);
var thenClause = parseClause(thenText);

var hit = new DslrGen.AtomicHit(
        row.ruleId(),
        ifClause,
        thenClause,
        row.errorCodes() // List<String>
);

// Now feed hit into the pipeline you already wired:
var sig = new DslrGen.SignatureBuilder().build(hit.ruleId(), hit.ifClause(), hit.thenClause());
var rr  = new DslrGen.Router().route(sig);
    if (!rr.ok()) { reasons.record(hit.ruleId(), rr.reason()); continue; }

// Build emitter context using stems (you can pass full dotted path too)
var ctx = new DslrGen.GenericEmitter.EmitContext(
        new DslrGen.GenericEmitter.Clause(extractStem(hit.ifClause().path()), sig.ifKind.op,  sig.ifKind.card,  hit.ifClause().values()),
        new DslrGen.GenericEmitter.Clause(extractStem(hit.thenClause().path()), sig.thenKind.op, sig.thenKind.card, hit.thenClause().values()),
        java.util.List.of(DslrGen.RuleIR.ThenEffect.errorCodes(hit.errorCodes()))
);
var ir   = new DslrGen.GenericEmitter().emit(sig, ctx);
var dslr = new DslrGen.DslrFormatter().toDslr(ir);
    dslrWriter.append(hit.ruleId(), dslr);
        }


private String extractStem(String dottedPath) {
    // Turn "GoodsItem.previousProcedure.code" -> "previousProcedure.code"
    if (dottedPath == null) return "";
    int dot = dottedPath.indexOf('.');
    return (dot > 0 && dot + 1 < dottedPath.length()) ? dottedPath.substring(dot + 1) : dottedPath;
}


IF:   GoodsItem.requestedProcedure.code equals "40A"
THEN: GoodsItem.previousProcedure.code is one of ["21","53","71"]
