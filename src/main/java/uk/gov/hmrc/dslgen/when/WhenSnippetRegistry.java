package uk.gov.hmrc.dslgen.when;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public final class WhenSnippetRegistry {
    private final Map<String, WhenSnippet> byId;
    private final Map<Var, List<WhenSnippet>> providersByVar;
    private final Map<String, String> idByPhrase; // <---- NEW

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonModel {
        public List<String> vars;
        public List<Map<String, Object>> snippets;
    }

    public WhenSnippetRegistry(InputStream json) {
        try {
            var mapper = new ObjectMapper();
            var jm = mapper.readValue(json, new TypeReference<JsonModel>() {});
            Map<String, WhenSnippet> temp = new LinkedHashMap<>();
            Map<String, String> rev = new HashMap<>(); // reverse lookup

            for (var s : jm.snippets) {
                String id = (String) s.get("id");
                String phrase = (String) s.get("phrase");
                @SuppressWarnings("unchecked")
                var provides = ((List<String>) s.getOrDefault("provides", List.of()))
                        .stream().map(Var::valueOf).collect(Collectors.toUnmodifiableSet());
                @SuppressWarnings("unchecked")
                var requires = ((List<String>) s.getOrDefault("requires", List.of()))
                        .stream().map(Var::valueOf).collect(Collectors.toUnmodifiableSet());

                WhenSnippet snippet = new WhenSnippet(id, phrase, provides, requires);
                temp.put(id, snippet);
                rev.put(phrase, id); // <---- keep phrase→id
            }
            this.byId = Collections.unmodifiableMap(temp);
            this.idByPhrase = Collections.unmodifiableMap(rev); // <---- assign reverse

            Map<Var, List<WhenSnippet>> prov = new EnumMap<>(Var.class);
            for (var v : Var.values()) prov.put(v, new ArrayList<>());
            for (var sn : byId.values()) for (var v : sn.provides) prov.get(v).add(sn);
            this.providersByVar = prov.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load when-snippets.json", e);
        }
    }

    public WhenSnippet get(String id) { return byId.get(id); }

    public Optional<WhenSnippet> providerOf(Var var) {
        var list = providersByVar.getOrDefault(var, List.of());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // <---- ADD THIS
    public String idForPhrase(String phrase) {
        return idByPhrase.get(phrase);
    }
}