package uk.gov.hmrc.rules.toggle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class RuleToggles {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> globalDsl;
    public List<RuleToggle> rules;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleToggle {
        public String id;       // exact rule header name: rule "..."
        public String file;     // e.g. rules/BR675_rules.dslr
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String dsl;      // e.g. rules/dsl/BR675_rules.dsl
        public boolean enabled;

        public RuleToggle() {}
        public RuleToggle(String id, String file, String dsl, boolean enabled) {
            this.id = id; this.file = file; this.dsl = dsl; this.enabled = enabled;
        }
    }

    public static RuleToggles load(Path jsonPath) {
        try (var in = Files.newInputStream(jsonPath)) {
            return mapper().readValue(in, RuleToggles.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read toggles JSON: " + jsonPath, e);
        }
    }
    public static void save(Path jsonPath, RuleToggles toggles) {
        try {
            Files.createDirectories(jsonPath.getParent());
            try (var out = Files.newOutputStream(jsonPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                mapper().writeValue(out, toggles);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write toggles JSON: " + jsonPath, e);
        }
    }
    private static ObjectMapper mapper() {
        var om = new ObjectMapper();
        om.enable(SerializationFeature.INDENT_OUTPUT);
        return om;
    }

    /** Preserve enabled flags by (id,file) pair; drop missing rules; add new rules disabled. */
    public static RuleToggles merge(RuleToggles existing, List<RuleToggle> discovered, List<String> globalDsl) {
        var result = new RuleToggles();
        result.globalDsl = (globalDsl == null || globalDsl.isEmpty()) ? null : new ArrayList<>(globalDsl);

        Map<String, Boolean> prev = new LinkedHashMap<>();
        if (existing != null && existing.rules != null) {
            for (var r : existing.rules) prev.put(key(r.id, r.file), r.enabled);
        }
        for (var r : discovered) r.enabled = prev.getOrDefault(key(r.id, r.file), false);

        discovered.sort(Comparator.comparing((RuleToggle r) -> r.file).thenComparing(r -> r.id));
        result.rules = discovered;
        return result;
    }
    private static String key(String id, String file) { return id + "|" + file; }
}
