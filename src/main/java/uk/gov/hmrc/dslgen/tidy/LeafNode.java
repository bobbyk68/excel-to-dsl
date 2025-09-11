package uk.gov.hmrc.dslgen.tidy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Leaf holds the template and the bound runtime value (from Excel capture). */
public final class LeafNode implements RuleNode {
    private final ConstraintCase constraint;        // usually EXISTS/ONE_OF/NONE/etc for a leaf, or ALL_OF as neutral
    private final String dslTemplate;               // may or may not contain " - "
    private final String value;                     // captured literal (e.g., "IH7")

    public LeafNode(ConstraintCase constraint, String dslTemplate, String value) {
        this.constraint  = Objects.requireNonNull(constraint);
        this.dslTemplate = Objects.requireNonNull(dslTemplate);
        this.value       = Objects.requireNonNull(value);
    }

    public String template() { return dslTemplate; }
    public String value()    { return value; }

    @Override public boolean isLeaf() { return true; }
    @Override public ConstraintCase constraint() { return constraint; }
    @Override public List<RuleNode> children() { return Collections.emptyList(); }
}
