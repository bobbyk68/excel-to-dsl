package uk.gov.hmrc.dslgen.dslgen.support;

import java.util.ArrayList;
import java.util.List;

public class TemplateDef {
    private String id;
    private List<String> patterns = new ArrayList<>();
    private String dsl;
    private List<String> requires = new ArrayList<>();

    public TemplateDef() {}
    public TemplateDef(String id) { this.id = id; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    public String getDsl() { return dsl; }
    public void setDsl(String dsl) { this.dsl = dsl; }
    public List<String> getRequires() { return requires; }
    public void setRequires(List<String> requires) { this.requires = requires; }
}
