package uk.gov.hmrc.dslgen.tidy;

import java.util.List;

public interface RuleNode {
    boolean isLeaf();
    ConstraintCase constraint();   // how this node combines (or wraps) its content
    List<RuleNode> children();     // empty for leaves
}
