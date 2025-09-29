package uk.gov.hmrc.dslgen;

public final class MergeSummaryEntry {
    private final String keptId;                    // e.g., "BR675_..."
    private final String ifCondition;               // the IF that was merged
    private final java.util.List<String> originalThens; // all THEN lines seen for this IF (pre-merge)
    private final String mergedThen;                // final THEN used after merge
    private final java.util.List<String> mergedIds; // ids merged INTO the kept id

    public MergeSummaryEntry(String keptId,
                             String ifCondition,
                             java.util.List<String> originalThens,
                             String mergedThen,
                             java.util.List<String> mergedIds) {
        this.keptId = keptId;
        this.ifCondition = ifCondition;
        this.originalThens = originalThens;
        this.mergedThen = mergedThen;
        this.mergedIds = mergedIds;
    }

    public String keptId() { return keptId; }
    public String ifCondition() { return ifCondition; }
    public java.util.List<String> originalThens() { return originalThens; }
    public String mergedThen() { return mergedThen; }
    public java.util.List<String> mergedIds() { return mergedIds; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Kept ID: ").append(keptId).append('\n');
        sb.append("IF: ").append(ifCondition).append('\n');
        sb.append("  Originals (THEN):\n");
        for (String t : originalThens) {
            sb.append("    - ").append(t).append('\n');
        }
        sb.append("  Merged THEN: ").append(mergedThen).append('\n');
        if (!mergedIds.isEmpty()) {
            sb.append("  Merged IDs: ").append(String.join(", ", mergedIds)).append('\n');
        } else {
            sb.append("  Merged IDs: (none)\n");
        }
        return sb.toString();
    }
}
