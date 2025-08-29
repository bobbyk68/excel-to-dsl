package uk.gov.hmrc.dslgen.core.model;
// uk/gov/hmrc/rules/core/model/Bundle.java

import java.util.List;
public record Bundle(List<Atomic> atomic, List<Composite> composite) {}