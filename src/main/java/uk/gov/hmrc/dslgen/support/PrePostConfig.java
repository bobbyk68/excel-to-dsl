package uk.gov.hmrc.dslgen.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads prepend/append boilerplate snippets for when/then from JSON.
 */
public class PrePostConfig {
    private final List<String> whenPre;
    private final List<String> whenPost;
    private final List<String> thenPre;
    private final List<String> thenPost;

    public PrePostConfig() {
        this.whenPre  = load("/config/when-prepost.json", "prepend");
        this.whenPost = load("/config/when-prepost.json", "append");
        this.thenPre  = load("/config/then-prepost.json", "prepend");
        this.thenPost = load("/config/then-prepost.json", "append");
    }

    public List<String> whenPrepend() { return whenPre; }
    public List<String> whenAppend()  { return whenPost; }
    public List<String> thenPrepend() { return thenPre; }
    public List<String> thenAppend()  { return thenPost; }

    private List<String> load(String resource, String key) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            var mapper = new ObjectMapper();
            Map<?,?> json = mapper.readValue(in, Map.class);
            Object v = json.get(key);
            if (v instanceof List<?> l) return l.stream().map(String::valueOf).toList();
            return List.of();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + resource + " key " + key, e);
        }
    }
}
