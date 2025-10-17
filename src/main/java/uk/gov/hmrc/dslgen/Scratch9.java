private static String normaliseValue(Operator op, String raw) {
    // Keep value for NOT_EXISTS – we need to show the required code(s) in the bullet.
    if (op == Operator.EXISTS) {
        // Pure existence checks (no specific code) can omit the value.
        return "";
    }
    if (raw == null || raw.isBlank()) {
        return "\"\"";
    }
    String s = raw.trim();

    // Preserve quoted lists or quoted singletons as-is
    if (s.contains(",") && (s.startsWith("\"") || s.startsWith("'"))) {
        return s;
    }

    // If list without quotes -> quote each token
    if (s.contains(",")) {
        String[] parts = s.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = quote(parts[i].trim());
        return String.join(",", parts);
    }

    // Single value -> ensure quoted
    return quote(s);
}

private static String quote(String v) {
    if (v == null || v.isBlank()) return "\"\"";
    String s = v.trim();
    boolean alreadyQuoted = (s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("'")  && s.endsWith("'"));
    return alreadyQuoted ? s : "\"" + s + "\"";
}


switch (operatorToken.toLowerCase()) {
        case "equals"   -> Operator.EQUALS;
    case "in"       -> Operator.IN;
    case "not in"   -> Operator.NOT_IN;
    case "not exists" -> Operator.NOT_EXISTS;  // <-- required
default         -> Operator.EQUALS;
}
