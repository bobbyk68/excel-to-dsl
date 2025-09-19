package uk.gov.hmrc.api;

public final class RuleRunContext {
    private final String opId;
    private final String label; // e.g. "DeclarationValidation"

    public RuleRunContext(String opId, String label) {
        this.opId = opId;
        this.label = label;
    }
    public String opId()   { return opId; }
    public String label()  { return label; }
}
