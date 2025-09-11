package uk.gov.hmrc.dslgen.tidy;

// ConstraintComposer.java  (DSL-level helpers)
public final class ConstraintComposer {
  public static final class ConstraintSpec {
    public ConstraintSpec(String template, java.util.List<String> values, ConstraintCase op){/*..*/}
    public String template(){/*..*/} public java.util.List<String> values(){/*..*/}
    public ConstraintCase operator(){/*..*/}
  }
  public java.util.List<String> composeDsl(ConstraintSpec spec){/*..*/}
  public String composeDrl(ConstraintSpec spec){/*..*/} // optional for later
}

// HyphenTwoPhaseComposer.java
public final class HyphenTwoPhaseComposer {
  public java.util.List<String> composeLeaf(ConstraintCase kase, String template, String value, String dataPath){/*..*/}
}
