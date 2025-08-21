// v1
package uk.gov.hmrc.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

// v2
package uk.gov.hmrc.rules.templates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class WhenTemplates {

    public record Template(
            String code,
            List<String> lines,
            List<String> prepend,
            List<String> append
    ) {}

    private final Map<String, Template> templates;

    private WhenTemplates(Map<String, Template> templates) {
        // normalize null lists to empty
        Map<String, Template> norm = new LinkedHashMap<>();
        for (var e : templates.entrySet()) {
            var t = e.getValue();
            norm.put(e.getKey(), new Template(
                    t.code(),
                    Optional.ofNullable(t.lines()).orElseGet(List::of),
                    Optional.ofNullable(t.prepend()).orElseGet(List::of),
                    Optional.ofNullable(t.append()).orElseGet(List::of)
            ));
        }
        this.templates = Collections.unmodifiableMap(norm);
    }

    public static WhenTemplates load(Path jsonPath) {
        try {
            var om = new ObjectMapper();
            Map<String, Template> map = om.readValue(jsonPath.toFile(),
                    new TypeReference<Map<String, Template>>() {});
            return new WhenTemplates(map);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WHEN templates: " + jsonPath, e);
        }
    }

    /** Renders the WHEN body (either single 'code' or joined 'lines'). */
    public String render(String id, Map<String, Object> bindings) {
        var t = get(id);
        if (t.code() != null && !t.code().isEmpty()) {
            return SimpleBindings.apply(t.code(), bindings);
        }
        if (!t.lines().isEmpty()) {
            var rendered = new StringBuilder();
            for (var ln : t.lines()) {
                rendered.append(SimpleBindings.apply(ln, bindings)).append("\n        ");
            }
            // remove trailing indent/newline if present
            return rendered.toString().replaceFirst("\\s+$", "");
        }
        throw new IllegalArgumentException("WHEN template '" + id + "' has neither 'code' nor 'lines'.");
    }

    /** Prepend lines (rendered) to inject before the WHEN block. */
    public List<String> prependLines(String id, Map<String, Object> bindings) {
        var t = get(id);
        return t.prepend().stream().map(s -> SimpleBindings.apply(s, bindings)).toList();
    }

    /** Append lines (rendered) to inject after the WHEN block (rarely used, but supported). */
    public List<String> appendLines(String id, Map<String, Object> bindings) {
        var t = get(id);
        return t.append().stream().map(s -> SimpleBindings.apply(s, bindings)).toList();
    }

    private Template get(String id) {
        var t = templates.get(id);
        if (t == null) throw new IllegalArgumentException("Missing WHEN template id: " + id);
        return t;
    }
}