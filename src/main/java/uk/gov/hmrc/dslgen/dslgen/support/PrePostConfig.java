package uk.gov.hmrc.dslgen.dslgen.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

public class PrePostConfig {
    private final PrePostFile when;
    private final PrePostFile then;

    public PrePostConfig() {
        this.when = load("/config/when-prepost.json");
        this.then = load("/config/then-prepost.json");
    }

    public List<String> whenPrepend() { return when.getPrepend() == null ? List.of() : when.getPrepend(); }
    public List<String> whenAppend()  { return when.getAppend()  == null ? List.of() : when.getAppend(); }
    public List<String> thenPrepend() { return then.getPrepend() == null ? List.of() : then.getPrepend(); }
    public List<String> thenAppend()  { return then.getAppend()  == null ? List.of() : then.getAppend(); }

    private PrePostFile load(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return new PrePostFile();
            return new ObjectMapper().readValue(in, PrePostFile.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
    }
}
