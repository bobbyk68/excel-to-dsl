package uk.gov.hmrc.dslgen.dslgen.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads regex→DSL templates from JSON files and supports dependency-aware matching. */
public class DslTemplateService {
    private final List<TemplateDef> whenDefs;
    private final List<TemplateDef> thenDefs;
    private final Map<String, TemplateDef> whenById;

    public DslTemplateService() {
        this.whenDefs = loadList("/config/when-templates.json");
        this.thenDefs = loadList("/config/then-templates.json");
        this.whenById = new HashMap<>();
        for (TemplateDef d : whenDefs) whenById.put(d.getId(), d);
    }

    public Optional<TemplateMatch> matchWhen(String phrase) { return match(phrase, whenDefs); }
    public Optional<TemplateMatch> matchThen(String phrase) { return match(phrase, thenDefs); }
    public Optional<TemplateDef> findWhenById(String id) { return Optional.ofNullable(whenById.get(id)); }

    private List<TemplateDef> loadList(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(in, new TypeReference<List<TemplateDef>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
    }

    private Optional<TemplateMatch> match(String phrase, List<TemplateDef> defs) {
        for (TemplateDef d : defs) {
            List<String> regexes = d.getPatterns();
            if (regexes == null) continue;
            for (Pattern p : compile(regexes)) {
                Matcher m = p.matcher(phrase);
                if (m.matches()) {
                    String out = substitute(d.getDsl(), m);
                    List<String> requires = d.getRequires() == null ? List.of() : d.getRequires();
                    return Optional.of(new TemplateMatch(d.getId(), out, requires));
                }
            }
        }
        return Optional.empty();
    }

    private List<Pattern> compile(List<String> regexes) {
        List<Pattern> list = new ArrayList<>();
        for (String r : regexes) list.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE));
        return list;
    }

    private String substitute(String template, Matcher m) {
        if (template == null) return "";
        String out = template;
        for (int i = 1; i <= m.groupCount(); i++) out = out.replace("{" + i + "}", m.group(i));
        return out;
    }

    public record TemplateMatch(String id, String dsl, List<String> requires) {}
}
