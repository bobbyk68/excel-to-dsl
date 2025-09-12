package uk.gov.hmrc.dslgen.tidy;

/**
 * Represents everything we parsed out of one Excel row,
 * ready for composition into DSL.
 */
// Put this tiny carrier inside the class to avoid new files
private static final class RowHits {
    final String businessRuleId;
    final String operatorToken;
    final List<AtomicHit> hits;
    RowHits(String businessRuleId, String operatorToken, List<AtomicHit> hits) {
        this.businessRuleId = businessRuleId;
        this.operatorToken  = operatorToken;
        this.hits = hits;
    }
}

