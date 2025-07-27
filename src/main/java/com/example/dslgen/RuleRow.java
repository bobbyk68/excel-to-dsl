// RuleRow.java
package com.example.dslgen;

public class RuleRow {
    private final String name;
    private final String procedureCategory;
    private final String declarationType;
    private final String condition;
    private final String errorCode;

    public RuleRow(String name, String procedureCategory, String declarationType, String condition, String errorCode) {
        this.name = name;
        this.procedureCategory = procedureCategory;
        this.declarationType = declarationType;
        this.condition = condition;
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

    public String getCondition() {
        return condition;
    }

    public String getErrorCode() {
        return errorCode;
    }
}