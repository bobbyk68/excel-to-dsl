package uk.gov.hmrc.dslgen.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Supports either a single-line DSL ("dsl": "…") or multi-line ("dsl": ["…","…"]).
 */
public class TemplateDef {
    private String id;
    private List<String> patterns = new ArrayList<>();
    /** optional single string form from JSON */
    private String dsl;
    /** optional multi-line form from JSON */
    private List<String> dslLines = new ArrayList<>();
    private List<String> requires = new ArrayList<>();

    public TemplateDef() {}
    public TemplateDef(String id) { this.id = id; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }

    public String getDsl() { return dsl; }
    public void setDsl(String dsl) { this.dsl = dsl; }

    public List<String> getDslLines() { return dslLines; }
    public void setDslLines(List<String> dslLines) { this.dslLines = dslLines; }

    public List<String> getRequires() { return requires; }
    public void setRequires(List<String> requires) { this.requires = requires; }

    /** Normalised access: always returns a list of DSL lines. */
    public List<String> effectiveDslLines() {
        if (dslLines != null && !dslLines.isEmpty()) return dslLines;
        if (dsl != null && !dsl.isBlank()) return List.of(dsl);
        return List.of();
    }
}
