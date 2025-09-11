package uk.gov.hmrc.dslgen.tidy;


import java.util.*;
import java.util.stream.Collectors;

public final class ConstraintComposer {

    public enum Operator { EXISTS, NONE, ANY_OF, ALL_OF, ONE_OF, NONE_OF }

    public static final class ConstraintSpec {
        private final String template;       // e.g. "goodsitem code equals {value}"
        private final List<String> values;   // e.g. ["IH7","IH8"]
        private final Operator operator;

        public ConstraintSpec(String template, List<String> values, Operator operator) {
            this.template = Objects.requireNonNull(template);
            this.values = List.copyOf(values);
            this.operator = Objects.requireNonNull(operator);
        }
        public String template() { return template; }
        public List<String> values() { return values; }
        public Operator operator() { return operator; }
    }

    public List<String> composeDsl(ConstraintSpec spec) {
        switch (spec.operator()) {
            case EXISTS:
                return List.of("exists (" + spec.template() + ")");
            case NONE:
                return List.of("not (" + spec.template() + ")");
            case ALL_OF:
                return spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.toList());
            case ANY_OF:
                return List.of("(" + spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining(" OR ")) + ")");
            case NONE_OF:
                return List.of("not (" + spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining(" OR ")) + ")");
            case ONE_OF:
                return List.of("/*@oneOf*/ (" + spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining(" OR ")) + ")");
            default:
                throw new IllegalArgumentException("Unsupported op: " + spec.operator());
        }
    }

    public String composeDrl(ConstraintSpec spec) {
        switch (spec.operator()) {
            case EXISTS:
                return "exists( " + spec.template() + " )";
            case NONE:
                return "not( " + spec.template() + " )";
            case ALL_OF:
                return spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining("\n"));
            case ANY_OF:
                return "(" + spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining(" or ")) + ")";
            case NONE_OF:
                return "not( " + spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining(" or ")) + " )";
            case ONE_OF:
                return  "accumulate( " + spec.values().stream().map(v -> bind(spec.template(), v)).collect(Collectors.joining(", "))
                        + " ; $cnt : count(1) )\n" + "eval( $cnt == 1 )";
            default:
                throw new IllegalArgumentException("Unsupported op: " + spec.operator());
        }
    }

    private static String bind(String template, String value) {
        return template.replace("{value}", escape(value)).replace("{1}", escape(value));
    }
    private static String escape(String raw) { return raw; }
}
