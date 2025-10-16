package uk.gov.hmrc.dslgen.emit;

public final class AtomicHitImpl implements AtomicHit {   // use your class name
    // existing fields
    private String anchorToken;   // e.g. "SP", "AI", "GI"
    private String fieldToken;    // e.g. "SP_CODE", "AI_CODE"
    private String operatorToken; // e.g. "equals", "in", "not in", "not exists"
    private String rawValue;      // e.g. 72M, MOVE3, "B03"

    // (optional) meta you already inferred
    private PatternIntrospector.AnchorScope anchorScope;
    private String anchorPath;
    private PatternIntrospector.FieldKey fieldKey;
    private PatternIntrospector.Operator operator;
    private PatternIntrospector.Quantifier quantifier;
    private boolean negated;
    private PatternIntrospector.PatternKind patternKind;

    // ctor + builder/setters you already have...
    @Override
    public String getAnchorToken() {
        return anchorToken == null ? "" : anchorToken;
    }

    @Override
    public String getFieldToken() {
        return fieldToken == null ? "" : fieldToken;
    }

    @Override
    public String getOperatorToken() {
        // normalise once here if you like
        if (operatorToken == null) return "equals";
        String t = operatorToken.trim().toLowerCase(Locale.ROOT);
        if ("not_in".equals(t)) return "not in";
        return t;
    }

    @Override
    public String getRawValue() {
        return rawValue == null ? "" : rawValue;
    }
    public AtomicHitImpl setFieldKey(PatternIntrospector.FieldKey v){ this.fieldKey=v; return this; }
    public AtomicHitImpl setAnchorScope(PatternIntrospector.AnchorScope v){ this.anchorScope=v; return this; }
    public AtomicHitImpl setAnchorPath(String v){ this.anchorPath=v; return this; }
    public AtomicHitImpl setOperator(PatternIntrospector.Operator v){ this.operator=v; return this; }
    public AtomicHitImpl setQuantifier(PatternIntrospector.Quantifier v){ this.quantifier=v; return this; }
    public AtomicHitImpl setNegated(boolean v){ this.negated=v; return this; }
    public AtomicHitImpl setPatternKind(PatternIntrospector.PatternKind v){ this.patternKind=v; return this; }

}

List<String> groups = collectGroups(m);   // your existing helper
String path = groups.get(0);
String op   = groups.size() > 1 ? groups.get(1) : "equals";
String val  = groups.size() > 2 ? groups.get(2) : "";

var tokens = PathMapper.tokensFor(path); // SP/SP_CODE, AI/AI_CODE, etc.

AtomicHitImpl hit = new AtomicHitImpl()
        .setTemplate(substituted.get(0))  // if you keep it
        .setGroups(groups)
        // the four tokens required by the adapter:
        .setAnchorToken(tokens.anchorTok)
        .setFieldToken(tokens.fieldTok)
        .setOperatorToken(op)
        .setRawValue(val);

// meta inferred from DSL (you already have this):
PatternIntrospector.Meta meta = PatternIntrospector.parse(ca.dsl());
hit.setAnchorScope(meta.anchorScope())
        .setAnchorPath(meta.anchorPath())
        .setFieldKey(meta.fieldKey())
        .setOperator(meta.operator())
        .setQuantifier(meta.quantifier())
        .setNegated(meta.negated())
        .setPatternKind(meta.patternKind());

Condition c = AtomicHitAdapter.toCondition(hit);  // compiles now
