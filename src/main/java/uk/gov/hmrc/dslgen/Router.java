// Add these helpers inside DslrGen (e.g., near runExample)
private static DslrGen.ParsedClause parseClause(String raw) {
    String s = raw.trim();
    // operator phrases (longer first so we match the longest)
    String[] OPS = {
            "does not exist", "not exists", "must not equal", "not equals", "is not",
            "must not be one of", "not in",
            "is one of", "must be one of", "equal to", "equals", "is",
            "in", "exists", "is present", "present"
    };

    int bestPos = -1, bestLen = -1;
    String bestOp = null;
    String lower = s.toLowerCase(java.util.Locale.ROOT);

    for (String op : OPS) {
        String needle = " " + op + " ";
        int pos = lower.indexOf(needle);
        // also allow terminal ops like "... exists" / "... does not exist"
        if (pos < 0) {
            if (lower.endsWith(" " + op)) pos = lower.lastIndexOf(" " + op);
        }
        if (pos >= 0 && op.length() > bestLen) {
            bestPos = pos;
            bestLen = op.length();
            bestOp = op;
        }
    }
    if (bestOp == null) throw new IllegalArgumentException("Unsupported operator in: " + raw);

    String path = s.substring(0, bestPos).trim();
    String tail = s.substring(bestPos + 1).trim(); // drop leading space
    String opToken = bestOp;
    String valuesPart = tail.substring(bestOp.length()).trim(); // may be empty

    java.util.List<String> values = java.util.List.of();
    if (!valuesPart.isEmpty()) {
        if (valuesPart.startsWith("[")) {
            values = parseList(valuesPart);
        } else if (valuesPart.startsWith("\"")) {
            values = java.util.List.of(unquote(valuesPart));
        } else {
            values = java.util.List.of(valuesPart);
        }
    }
    return new DslrGen.ParsedClause(path, opToken, values);
}

private static java.util.List<String> parseList(String part) {
    String inner = part.trim();
    if (!inner.startsWith("[") || !inner.endsWith("]"))
        throw new IllegalArgumentException("Bad list syntax: " + part);
    inner = inner.substring(1, inner.length() - 1).trim();
    if (inner.isEmpty()) return java.util.List.of();

    java.util.ArrayList<String> out = new java.util.ArrayList<>();
    int i = 0;
    while (i < inner.length()) {
        while (i < inner.length() && (inner.charAt(i) == ',' || Character.isWhitespace(inner.charAt(i)))) i++;
        if (i >= inner.length()) break;
        if (inner.charAt(i) == '"') {
            int j = ++i;
            StringBuilder sb = new StringBuilder();
            while (j < inner.length() && inner.charAt(j) != '"') sb.append(inner.charAt(j++));
            if (j >= inner.length()) throw new IllegalArgumentException("Unclosed quote in: " + part);
            out.add(sb.toString());
            i = j + 1;
        } else {
            int j = i;
            while (j < inner.length() && inner.charAt(j) != ',') j++;
            out.add(inner.substring(i, j).trim());
            i = j;
        }
    }
    return out;
}

private static String unquote(String s) {
    s = s.trim();
    return (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) ? s.substring(1, s.length() - 1) : s;
}

private static String stemAfterRoot(String dottedPath) {
    if (dottedPath == null) return "";
    int dot = dottedPath.indexOf('.');
    return (dot > 0 && dot + 1 < dottedPath.length()) ? dottedPath.substring(dot + 1) : dottedPath;
}

// Replace your existing runExample() with this one:
public static void runExample() {
    // 1) Raw IF/THEN strings (as they’d come from the sheet)
    String ifText   = "GoodsItem.requestedProcedure.code equals \"VAL1\"";
    String thenText = "GoodsItem.previousProcedure.code is one of [\"VAL2\",\"VAL3\"]";
    java.util.List<String> errorCodes = java.util.List.of("ERRCODE_R1");

    // 2) Parse into ParsedClause
    var ifClauseParsed   = parseClause(ifText);
    var thenClauseParsed = parseClause(thenText);

    // 3) Build Signature (classification only)
    var sig = new DslrGen.SignatureBuilder().build("R1", ifClauseParsed, thenClauseParsed);

    // 4) Route (tiny gate)
    var rr  = new DslrGen.Router().route(sig);
    if (!rr.ok()) {
        System.out.println("// fail-fast: " + rr.reason());
        return;
    }

    // 5) Build EmitContext (use a readable stem for the path the user sees)
    var emitCtx = new DslrGen.GenericEmitter.EmitContext(
            new DslrGen.GenericEmitter.Clause(
                    stemAfterRoot(ifClauseParsed.path()),  // e.g., "requestedProcedure.code"
                    sig.ifKind.op, sig.ifKind.card, ifClauseParsed.values()
            ),
            new DslrGen.GenericEmitter.Clause(
                    stemAfterRoot(thenClauseParsed.path()), // e.g., "previousProcedure.code"
                    sig.thenKind.op, sig.thenKind.card, thenClauseParsed.values()
            ),
            java.util.List.of(DslrGen.RuleIR.ThenEffect.errorCodes(errorCodes))
    );

    // 6) Emit IR (THEN is negated here) and format DSLR (two shapes only)
    var ir   = new DslrGen.GenericEmitter().emit(sig, emitCtx);
    var dslr = new DslrGen.DslrFormatter().toDslr(ir);
    System.out.println(dslr);
}
