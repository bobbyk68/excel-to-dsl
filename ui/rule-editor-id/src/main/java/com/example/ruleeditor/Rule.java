package com.example.ruleeditor;

import java.util.UUID;

public class Rule {
    private String id;
    private String patternType;
    private String lhs;
    private String rhs;

    public Rule() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPatternType() { return patternType; }
    public void setPatternType(String patternType) { this.patternType = patternType; }
    public String getLhs() { return lhs; }
    public void setLhs(String lhs) { this.lhs = lhs; }
    public String getRhs() { return rhs; }
    public void setRhs(String rhs) { this.rhs = rhs; }
}