package uk.gov.hmrc.rules;

import java.util.Map;

final class SimpleBindings {
    static String apply(String template, Map<String, Object> bindings) {
        var out = template;
        for (var e : bindings.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }
    private SimpleBindings() {}
}