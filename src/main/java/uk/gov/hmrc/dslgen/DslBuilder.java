package uk.gov.hmrc.dslgen;

import uk.gov.hmrc.dslgen.when.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class DslBuilder {

    // ---- ctor params (unchanged shape) ----
    private final PatternMatcher whenMatcher;
    private final PatternMatcher thenMatcher;
    private final Path whenTemplatePath;
    private final Path thenTemplatePath;

    // ---- new dependency-aware pieces ----
    private final WhenSnippetRegistry reg;
    private final WhenAssembler whenAssembler;

    // ---- loaded templates (prepend/append phrases) ----
    private final Template whenTemplate;
    private final Template thenTemplate;

    public DslBuilder(PatternMatcher whenMatcher,
                      PatternMatcher thenMatcher,
                      Path whenTemplatePath,
                      Path thenTemplatePath) {
        this.whenMatcher = whenMatcher;
        this.thenMatcher = thenMatcher;
        this.whenTemplatePath = whenTemplatePath;
        this.thenTemplatePath = thenTemplatePath;

        InputStream json = getClass().getClassLoader()
                .getResourceAsStream("config/when-snippets.json");
        this.reg = new WhenSnippetRegistry(Objects.requireNonNull(json, "when-snippets.json missing"));
        this.whenAssembler = new WhenAssembler(reg);

        this.whenTemplate = TemplateLoader.load(whenTemplatePath);
        this.thenTemplate = TemplateLoader.load(thenTemplatePath);
    }

    public ResultAll build(List<RuleRow> rows) {
        ResultAll result = new ResultAll();
        StringBuilder dslr = new StringBuilder();

        for (RuleRow row : rows) {
            dslr.append("rule \"").append(row.ruleName()).append("\"\nwhen\n");

            // --- WHEN: template prepend + matcher + template append ---
            List<String> whenPhrasesRaw = new ArrayList<>();
            whenPhrasesRaw.addAll(whenTemplate.prepend);
            whenMatcher.tryMatch(row).ifPresent(whenPhrasesRaw::add);
            whenPhrasesRaw.addAll(whenTemplate.append);

            // phrases -> IDs for dependency resolution
            List<String> whenIds = whenPhrasesRaw.stream()
                    .map(reg::idForPhrase)
                    .filter(Objects::nonNull)
                    .toList();

            // Resolve dependencies & order (inject binders like $doc chain), then hydrate
            List<String> whenPhrasesOrdered = whenAssembler.assemblePhrases(whenIds);
            List<String> whenPhrases = hydratePlaceholders(whenPhrasesOrdered, row);

            for (String p : whenPhrases) dslr.append("    ").append(p).append("\n");

            // --- THEN ---
            dslr.append("then\n");
            List<String> thenLines = new ArrayList<>();
            thenLines.addAll(thenTemplate.prepend);
            thenMatcher.tryMatch(row).ifPresent(thenLines::add);
            thenLines.addAll(thenTemplate.append);

            for (String t : thenLines) dslr.append("    ").append(t).append("\n");
            dslr.append("end\n\n");

            result.addRule(row.ruleName(), whenPhrases, thenLines);
        }

        // Write combined DSLR text to WHEN template path’s sibling (or adjust to your output path)
        Path out = deriveDslrOutputPath(whenTemplatePath);
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, dslr.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing DSLR to " + out, e);
        }

        return result;
    }

    private Path deriveDslrOutputPath(Path whenTemplatePath) {
        // Heuristic: if template is ".../when-template.json", write ".../generated-rules.dslr"
        Path parent = whenTemplatePath.getParent() != null ? whenTemplatePath.getParent() : Paths.get(".");
        return parent.resolve("generated-rules.dslr");
    }

    private List<String> hydratePlaceholders(List<String> phrases, RuleRow row) {
        List<String> out = new ArrayList<>(phrases.size());
        for (String p : phrases) {
            String q = p;
            if (q.contains("{decTypes}"))
                q = q.replace("{decTypes}", String.join(",", row.declarationTypes()));
            if (q.contains("{procCats}"))
                q = q.replace("{procCats}", String.join(",", row.procedureCategories()));
            if (q.contains("{val}"))
                q = q.replace("{val}", row.param());
            out.add(q);
        }
        return out;
    }

    // ---------- Support types (unchanged contracts) ----------
    public interface RuleRow {
        String ruleName();
        List<String> declarationTypes();
        List<String> procedureCategories();
        String param();
        String errorMessage();
    }

    public interface PatternMatcher {
        /** Return a WHEN/THEN phrase if matched; empty otherwise. */
        Optional<String> tryMatch(RuleRow row);
    }

    public static final class ResultAll {
        private final Map<String, Map<String, List<String>>> rules = new HashMap<>();
        public void addRule(String name, List<String> when, List<String> then) {
            rules.put(name, Map.of("when", when, "then", then));
        }
        public Map<String, Map<String, List<String>>> getRules() { return rules; }
    }

    // ---------- Template loading (keeps your 2-path setup) ----------
    private record Template(List<String> prepend, List<String> append) {
        static Template empty() { return new Template(List.of(), List.of()); }
    }

    /** Supports:
     *  1) JSON with {"prepend":[...], "append":[...]}
     *  2) Plain text file: each non-empty line is a phrase (treated as prepend)
     */
    private static final class TemplateLoader {
        static Template load(Path path) {
            if (path == null) return Template.empty();
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (text.startsWith("{")) {
                    // JSON
                    var map = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(text, Map.class);
                    List<String> prepend = toList(map.get("prepend"));
                    List<String> append  = toList(map.get("append"));
                    return new Template(prepend, append);
                } else {
                    // Line-based
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                            .map(String::trim)
                            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                            .collect(Collectors.toList());
                    return new Template(lines, List.of());
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load template: " + path, e);
            }
        }
        @SuppressWarnings("unchecked")
        private static List<String> toList(Object o) {
            if (o == null) return List.of();
            if (o instanceof List<?> l) return l.stream().map(String::valueOf).toList();
            return List.of(String.valueOf(o));
        }
    }
}