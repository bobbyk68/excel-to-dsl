/* ------------ add inside GenericEmitter ------------- */
public static final class Clause {
    private final String path;                // canonical path e.g. "goodsItem.previousProcedure.code"
    private final String op;                  // canonical operator symbol e.g. "==", "in", "exists"
    private final java.util.List<String> values; // normalized, can be empty

    public Clause(String path, String op, java.util.List<String> values) {
        this.path = path == null ? "" : path;
        this.op = op == null ? "" : op;
        this.values = values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }
    public String path()   { return path; }
    public String op()     { return op; }
    public java.util.List<String> values() { return values; }

    // convenience
    public boolean isExists() { return "exists".equalsIgnoreCase(op); }
    public boolean hasNoValues() { return values.isEmpty(); }
}
/* ------------ end addition ------------- */
