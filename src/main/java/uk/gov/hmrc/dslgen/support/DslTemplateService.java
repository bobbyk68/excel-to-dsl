package uk.gov.hmrc.dslgen.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads templates and performs regex matching + placeholder substitution.
 * Now returns TemplateMatch with a LIST of DSL lines.
 */
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
                    // Substitute across all effective DSL lines
                    List<String> substituted = substituteAll(d.effectiveDslLines(), m);
                    List<String> requires = d.getRequires() == null ? List.of() : d.getRequires();
                    return Optional.of(new TemplateMatch(d.getId(), substituted, requires));
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

    /** Substitute {1},{2} and named groups {name} across all lines. */
    private List<String> substituteAll(List<String> lines, Matcher m) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(substitute(line, m));
        return out;
    }

    private String substitute(String template, Matcher m) {
        if (template == null) return "";
        String out = template;

        // numbered groups
        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            if (g != null) out = out.replace("{" + i + "}", g);
        }

        // named groups
        java.util.regex.Matcher tokenMatcher = Pattern
                .compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
                .matcher(out);

        StringBuffer sb = new StringBuffer();
        while (tokenMatcher.find()) {
            String name = tokenMatcher.group(1);
            String replacement;
            try {
                String g = m.group(name);
                replacement = (g != null) ? g : "{" + name + "}";
            } catch (IllegalArgumentException ex) {
                replacement = "{" + name + "}";
            }
            tokenMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        tokenMatcher.appendTail(sb);
        return sb.toString();
    }

    /** Carries id + multiple lines + requires. */
    public record TemplateMatch(String id, List<String> dslLines, List<String> requires) {}
}
