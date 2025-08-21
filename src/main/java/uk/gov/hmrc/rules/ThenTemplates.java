package uk.gov.hmrc.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class ThenTemplates {
    private final Map<String, Template> templates;
    public record Template(String code) {}

    private ThenTemplates(Map<String, Template> templates) { this.templates = templates; }

    public static ThenTemplates load(Path jsonPath) {
        try {
            var om = new ObjectMapper();
            var map = om.readValue(jsonPath.toFile(),
                    om.getTypeFactory().constructMapType(Map.class, String.class, Template.class));
            return new ThenTemplates(map);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load THEN templates: " + jsonPath, e);
        }
    }

    public String render(String id, Map<String, Object> bindings) {
        var t = templates.get(id);
        if (t == null) throw new IllegalArgumentException("Missing THEN template id: " + id);
        return SimpleBindings.apply(t.code(), bindings);
    }
}