package uk.gov.hmrc.dslgen.tidy;

// ConstraintCase.java
public enum ConstraintCase { EXISTS, NONE, ANY_OF, ALL_OF, ONE_OF, NONE_OF }

// RuleNode.java
public interface RuleNode {
  boolean isLeaf();
  ConstraintCase constraint();
  java.util.List<RuleNode> children();
}

// LeafNode.java
public final class LeafNode implements RuleNode {
  public LeafNode(ConstraintCase constraint, String dslTemplate, String value, String dataPath) { /*..*/ }
  public String template(){/*..*/} public String value(){/*..*/} public String dataPath(){/*..*/}
  public boolean isLeaf(){ return true; } public ConstraintCase constraint(){/*..*/}
  public java.util.List<RuleNode> children(){ return java.util.List.of(); }
}

// InternalNode.java
public final class InternalNode implements RuleNode {
  public InternalNode(ConstraintCase constraint, java.util.List<RuleNode> children){/*..*/}
  public boolean isLeaf(){ return false; } public ConstraintCase constraint(){/*..*/}
  public java.util.List<RuleNode> children(){/*..*/}
}
