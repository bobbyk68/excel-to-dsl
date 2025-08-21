// v1
package uk.gov.hmrc.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class WhenTemplates {
    private final Map<String, Template> templates;
    public record Template(String code) {}

    private WhenTemplates(Map<String, Template> templates) { this.templates = templates; }

    public static WhenTemplates load(Path jsonPath) {
        try {
            var om = new ObjectMapper();
            var map = om.readValue(jsonPath.toFile(),
                    om.getTypeFactory().constructMapType(Map.class, String.class, Template.class));
            return new WhenTemplates(map);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WHEN templates: " + jsonPath, e);
        }
    }

    public String render(String id, Map<String, Object> bindings) {
        var t = templates.get(id);
        if (t == null) throw new IllegalArgumentException("Missing WHEN template id: " + id);
        return SimpleBindings.apply(t.code(), bindings);
    }
}