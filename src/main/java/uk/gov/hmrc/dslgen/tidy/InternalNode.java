package uk.gov.hmrc.dslgen.tidy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Internal node wraps/combines its children with a ConstraintCase. */
public final class InternalNode implements RuleNode {
    private final ConstraintCase constraint;
    private final List<RuleNode> children;

    public InternalNode(ConstraintCase constraint, List<RuleNode> children) {
        this.constraint = Objects.requireNonNull(constraint);
        this.children   = new ArrayList<>(Objects.requireNonNull(children));
    }

    @Override public boolean isLeaf() { return false; }
    @Override public ConstraintCase constraint() { return constraint; }
    @Override public List<RuleNode> children() { return children; }
}
