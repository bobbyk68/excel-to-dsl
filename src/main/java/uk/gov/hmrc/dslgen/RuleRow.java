package uk.gov.hmrc.dslgen;

import java.util.List;

public class RuleRow {
    private final String id;
    private final String ifCondition;     // Excel IF (English)
    private final String thenCondition;   // Excel THEN (English)
    private String errorCode;
    private List<String> declarationType;
    private List<String> procedureCategory;
    private String param;

    // =========================================
    // NEW: merged THEN codes (distinct, ordered)
    // =========================================
    private List<String> mergedThenCodes;   // e.g., ["VG1","V","CX"]
    private String mergedThenCodesCsv;      // e.g., "VG1,V,CX"

    public String id() { return id; }
    public String ifCondition() { return ifCondition; }
    public String thenCondition() { return thenCondition; }
    public String errorCode() { return errorCode; }
    public List<String> declarationType() { return declarationType; }
    public List<String> procedureCategory() { return procedureCategory; }
    public String param() { return param; }

    public List<String> mergedThenCodes() { return mergedThenCodes; }
    public String mergedThenCodesCsv() { return mergedThenCodesCsv; }
    public void setMergedThenCodes(List<String> codes) { this.mergedThenCodes = codes; }
    public void setMergedThenCodesCsv(String csv) { this.mergedThenCodesCsv = csv; }

    public void ensureMergedThenCodes() {
        if (this.mergedThenCodes == null) this.mergedThenCodes = new java.util.ArrayList<>();
    }

    public RuleRow(String businessRuleId,
                   List<String> declarationType,
                   List<String> procedureCategory,
                   String param,
                   String ifCondition,
                   String thenCondition,
                   String errorCode) {
        this.id = businessRuleId;
        this.declarationType = declarationType;
        this.procedureCategory = procedureCategory;
        this.param = param;
        this.errorCode = errorCode;
        this.ifCondition = ifCondition;
        this.thenCondition = thenCondition;
    }
}
