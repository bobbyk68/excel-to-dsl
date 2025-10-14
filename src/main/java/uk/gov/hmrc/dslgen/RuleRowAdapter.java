package uk.gov.hmrc.dslgen;

import uk.gov.hmrc.dslgen.emit.AtomicHitAdapter;

public final class RuleRowAdapter {
    private RuleRowAdapter() {}

    private record Hit(String a, String f, String o, String v)
            implements AtomicHitAdapter.AtomicHit {
        public String getAnchorToken()   { return a; }
        public String getFieldToken()    { return f; }
        public String getOperatorToken() { return o; }
        public String getRawValue()      { return v; }
    }

    public static AtomicHitAdapter.AtomicHit left(RuleRow r) {
        return new Hit(r.leftAnchor(), r.leftField(), r.leftOp(), r.leftVal());
    }

    public static AtomicHitAdapter.AtomicHit right(RuleRow r) {
        return new Hit(r.rightAnchor(), r.rightField(), r.rightOp(), r.rightVal());
    }

    /** Build a THEN “hit” carrying only the token (others blank). */
    public static AtomicHitAdapter.AtomicHit then(RuleRow r) {
        String tok = r.thenToken();
        if (tok == null || tok.isBlank()) return null;
        return new Hit("", tok, "", "");
    }
}
