// RuleRow.java
package com.example.dslgen;

import java.util.List;

public class RuleRow {
    private final String name;
    private final String procedureCategory;
    private final String declarationType;
    private final List<String> conditions;
    private final String errorCode;

    public RuleRow(String name, String procedureCategory, String declarationType, List<String> condition, String errorCode) {
        this.name = name;
        this.procedureCategory = procedureCategory;
        this.declarationType = declarationType;
        this.conditions = condition;
        this.errorCode = errorCode;
    }

    public String getName() {
        return name;
    }

    public String getProcedureCategory() {
        return procedureCategory;
    }

    public String getDeclarationType() {
        return declarationType;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public String getErrorCode() {
        return errorCode;
    }
}