// src/main/java/uk/gov/hmrc/rules/model/AtomicHitView.java
package uk.gov.hmrc.dslgen.tidy;

public record AtomicHitView(
        String dslTemplate,   // e.g. "there must only one Goodsitem special procedure - with code {value}"
        String rightLiteral,  // e.g. "IH7"
        String dataPath       // e.g. "additionalDocument.specialProcedure.type.code"
) {}
