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
    public List<String> globalDsl;  // e.g. ["rules/dsl/common.dsl", ...]
    public List<RuleToggle> rules;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleToggle {
        public String id;       // e.g. "BR236"
        public boolean enabled; // default false on first generation

        public RuleToggle() {}
        public RuleToggle(String id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
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

    /** Merge: keep existing enabled flags; add new IDs as disabled by default; drop IDs whose files disappeared. */
    public static RuleToggles merge(RuleToggles existing, Set<String> discoveredIds, List<String> globalDsl) {
        var result = new RuleToggles();
        result.globalDsl = (globalDsl == null || globalDsl.isEmpty()) ? null : new ArrayList<>(globalDsl);

        Map<String, Boolean> existingFlags = existing != null && existing.rules != null
                ? existing.rules.stream().collect(Collectors.toMap(r -> r.id, r -> r.enabled, (a,b)->b, LinkedHashMap::new))
                : new LinkedHashMap<>();

        // Preserve flags for still-present IDs
        List<RuleToggle> merged = new ArrayList<>();
        for (String id : discoveredIds) {
            boolean enabled = existingFlags.getOrDefault(id, false);
            merged.add(new RuleToggle(id, enabled));
        }

        // Sort by id for deterministic file
        merged.sort(Comparator.comparing(rt -> rt.id));
        result.rules = merged;
        return result;
    }
}
