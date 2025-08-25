package uk.gov.hmrc.dslgen.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DslTemplateService {
    private final List<Entry> whenEntries;
    private final List<Entry> thenEntries;

    // NEW: keep an ID map for WHEN templates
    private final Map<String, Entry> whenById;

    public DslTemplateService() {
        this.whenEntries = load("/config/when-templates.json");
        this.thenEntries = load("/config/then-templates.json");
        this.whenById = new HashMap<>();
        for (Entry e : whenEntries) whenById.put(e.id, e);
    }

    // NEW: expose lookup
    public Optional<TemplateDef> findWhenById(String id) {
        Entry e = whenById.get(id);
        return e == null ? Optional.empty() : Optional.of(new TemplateDef(e.id, e.dsl, e.requires));
    }

    // NEW: simple definition we can inject as a match
    public record TemplateDef(String id, String dsl, List<String> requires) {}
    
    public Optional<TemplateMatch> matchWhen(String phrase) {
        return match(phrase, whenEntries);
    }
    public Optional<TemplateMatch> matchThen(String phrase) {
        return match(phrase, thenEntries);
    }

    private Optional<TemplateMatch> match(String phrase, List<Entry> entries) {
        for (Entry e : entries) {
            for (Pattern p : e.patterns) {
                Matcher m = p.matcher(phrase);
                if (m.matches()) {
                    String out = e.dsl;
                    for (int i = 1; i <= m.groupCount(); i++) {
                        out = out.replace("{" + i + "}", m.group(i));
                    }
                    return Optional.of(new TemplateMatch(e.id, out, e.requires));
                }
            }
        }
        return Optional.empty();
    }

    private List<Entry> load(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return List.of();
            var mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            List<Map<String,Object>> list = mapper.readValue(in, List.class);
            List<Entry> out = new ArrayList<>();
            for (Map<String,Object> m : list) {
                @SuppressWarnings("unchecked")
                List<String> patterns = (List<String>) m.getOrDefault("patterns", List.of());
                String dsl = String.valueOf(m.get("dsl"));
                List<String> requires = (List<String>) m.getOrDefault("requires", List.of());
                List<Pattern> compiled = new ArrayList<>();
                for (String s : patterns) compiled.add(Pattern.compile(s, Pattern.CASE_INSENSITIVE));
                out.add(new Entry(
                        (String)m.getOrDefault("id",""),
                        compiled,
                        dsl,
                        requires
                ));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + resourcePath, e);
        }
    }

    public record TemplateMatch(String id, String dsl, List<String> requires) {}

    private static final class Entry {
        final String id;
        final List<Pattern> patterns;
        final String dsl;
        final List<String> requires;
        Entry(String id, List<Pattern> patterns, String dsl, List<String> requires) {
            this.id=id; this.patterns=patterns; this.dsl=dsl; this.requires=requires;
        }
    }

}
