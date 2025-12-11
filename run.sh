==== FILE: rules-text-parser-ir-demo/pom.xml ====
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>uk.gov.hmrc</groupId>
    <artifactId>rules-text-parser-ir-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>rules-text-parser-ir-demo</name>
    <description>Text-based rule parser + IR demo</description>
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- No special deps; just Java 17 -->
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
            </plugin>
        </plugins>
    </build>
</project>
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/RuleRow.java ====
package uk.gov.hmrc.rules;

import java.util.List;

/**
 * Minimal RuleRow used in the demo.
 * You already have your own RuleRow in your real project;
 * this one is just for the standalone smoke test.
 */
public class RuleRow {

    private final String id;
    private final List<String> declarationType;
    private final List<String> procedureCategory;
    private final String ifCondition;
    private final String thenCondition;
    private final String errorCode;
    private final String param;

    public RuleRow(String id,
                   List<String> declarationType,
                   List<String> procedureCategory,
                   String ifCondition,
                   String thenCondition,
                   String errorCode,
                   String param) {
        this.id = id;
        this.declarationType = List.copyOf(declarationType);
        this.procedureCategory = List.copyOf(procedureCategory);
        this.ifCondition = ifCondition;
        this.thenCondition = thenCondition;
        this.errorCode = errorCode;
        this.param = param;
    }

    public String id() {
        return id;
    }

    public List<String> declarationType() {
        return declarationType;
    }

    public List<String> procedureCategory() {
        return procedureCategory;
    }

    public String ifCondition() {
        return ifCondition;
    }

    public String thenCondition() {
        return thenCondition;
    }

    public String errorCode() {
        return errorCode;
    }

    public String param() {
        return param;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/parsing/ConditionRole.java ====
package uk.gov.hmrc.rules.parsing;

public enum ConditionRole {
    PRIMARY,
    SECONDARY,
    OTHER
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/parsing/ParsedCondition.java ====
package uk.gov.hmrc.rules.parsing;

import java.util.List;

public class ParsedCondition {

    private final String entityType;
    private final String parentAnchorKey;
    private final String fieldName;
    private final String operator;
    private final List<String> values;
    private final String fieldTypeLabel;
    private final ConditionRole role;

    public ParsedCondition(String entityType,
                           String parentAnchorKey,
                           String fieldName,
                           String operator,
                           List<String> values,
                           String fieldTypeLabel,
                           ConditionRole role) {
        this.entityType = entityType;
        this.parentAnchorKey = parentAnchorKey;
        this.fieldName = fieldName;
        this.operator = operator;
        this.values = List.copyOf(values);
        this.fieldTypeLabel = fieldTypeLabel;
        this.role = role;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getParentAnchorKey() {
        return parentAnchorKey;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOperator() {
        return operator;
    }

    public List<String> getValues() {
        return values;
    }

    public String getFieldTypeLabel() {
        return fieldTypeLabel;
    }

    public ConditionRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "ParsedCondition{" +
               "entityType='" + entityType + '\'' +
               ", parentAnchorKey='" + parentAnchorKey + '\'' +
               ", fieldName='" + fieldName + '\'' +
               ", operator='" + operator + '\'' +
               ", values=" + values +
               ", fieldTypeLabel='" + fieldTypeLabel + '\'' +
               ", role=" + role +
               '}';
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/parsing/DomainFieldDescriptor.java ====
package uk.gov.hmrc.rules.parsing;

public class DomainFieldDescriptor {

    private final String entityType;
    private final String parentAnchorKey;
    private final String fieldName;
    private final String fieldTypeLabel;

    public DomainFieldDescriptor(String entityType,
                                 String parentAnchorKey,
                                 String fieldName,
                                 String fieldTypeLabel) {
        this.entityType = entityType;
        this.parentAnchorKey = parentAnchorKey;
        this.fieldName = fieldName;
        this.fieldTypeLabel = fieldTypeLabel;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getParentAnchorKey() {
        return parentAnchorKey;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldTypeLabel() {
        return fieldTypeLabel;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/parsing/DomainFieldResolver.java ====
package uk.gov.hmrc.rules.parsing;

/**
 * Maps canonical paths like "GoodsItem.specialProcedures.code"
 * to entityType, anchor, fieldName, and a human label.
 *
 * This replaces your JSON combo table – you only maintain
 * field-level metadata here, not IF/THEN combos.
 */
public class DomainFieldResolver {

    public DomainFieldDescriptor resolve(String canonicalPath) {
        String path = canonicalPath.trim();

        if (path.equalsIgnoreCase("GoodsItem.specialProcedures.code")) {
            return new DomainFieldDescriptor(
                "SpecialProcedure",
                "GI",
                "code",
                "special procedure"
            );
        }

        if (path.equalsIgnoreCase("GoodsItem.previousProcedure.code")) {
            return new DomainFieldDescriptor(
                "GoodsItem",
                "GI",
                "previousProcedureCode",
                "previous procedure"
            );
        }

        if (path.equalsIgnoreCase("GoodsItem.requestedProcedure.code")) {
            return new DomainFieldDescriptor(
                "GoodsItem",
                "GI",
                "requestedProcedureCode",
                "requested procedure"
            );
        }

        if (path.equalsIgnoreCase("GoodsItem.additionalInformation.code")) {
            return new DomainFieldDescriptor(
                "AdditionalInformation",
                "GI",
                "code",
                "additional information"
            );
        }

        if (path.equalsIgnoreCase("GoodsItem.additionalDocuments.type.code")) {
            return new DomainFieldDescriptor(
                "AdditionalDocument",
                "GI",
                "typeCode",
                "additional document type"
            );
        }

        if (path.equalsIgnoreCase("Declaration.AuthorizationHolder.authorizationType.code")) {
            return new DomainFieldDescriptor(
                "AuthorizationHolder",
                "DECL",
                "authorizationTypeCode",
                "authorization type"
            );
        }

        // Fallback: not recognised – still usable for debugging
        return new DomainFieldDescriptor(
            "UnknownEntity",
            "GI",
            lastSegment(path),
            lastSegment(path)
        );
    }

    private String lastSegment(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/parsing/ConditionParser.java ====
package uk.gov.hmrc.rules.parsing;

public interface ConditionParser {

    ParsedCondition parseIf(String ifText);

    ParsedCondition parseThen(String thenText);
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/parsing/TextConditionParser.java ====
package uk.gov.hmrc.rules.parsing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Text-based parser that splits:
 *   FRONT   (quantifier)
 *   MIDDLE  (canonical path)
 *   TAIL    (operator phrase + values)
 */
public class TextConditionParser implements ConditionParser {

    private final DomainFieldResolver fieldResolver = new DomainFieldResolver();

    @Override
    public ParsedCondition parseIf(String ifText) {
        return parseClause(ifText, ConditionRole.PRIMARY);
    }

    @Override
    public ParsedCondition parseThen(String thenText) {
        return parseClause(thenText, ConditionRole.SECONDARY);
    }

    private ParsedCondition parseClause(String text, ConditionRole role) {
        if (text == null) {
            throw new IllegalArgumentException("Clause text is null");
        }

        String raw   = text.trim();
        String lower = raw.toLowerCase();

        // 1) FRONT – quantifier
        String quantifierPhrase = null;
        if (lower.startsWith("there is at least one ")) {
            quantifierPhrase = "there is at least one ";
        } else if (lower.startsWith("at least one ")) {
            quantifierPhrase = "at least one ";
        } else if (lower.startsWith("all ")) {
            quantifierPhrase = "all ";
        }

        String afterQuantifier;
        if (quantifierPhrase != null) {
            afterQuantifier = raw.substring(quantifierPhrase.length()).trim();
        } else {
            afterQuantifier = raw;
        }

        // 2) TAIL – operator phrase
        String[] operatorPhrases = new String[] {
            " must not be one of ",
            " must be one of ",
            " must not equal ",
            " must equals ",
            " must equal ",
            " equals ",
            " not equals "
        };

        String lowerAfter = afterQuantifier.toLowerCase();
        int operatorIndex = -1;
        String matchedOperatorPhrase = null;

        for (String phrase : operatorPhrases) {
            String pLower = phrase.toLowerCase();
            int idx = lowerAfter.indexOf(pLower.trim());
            if (idx >= 0 && (operatorIndex == -1 || idx < operatorIndex)) {
                operatorIndex = idx;
                matchedOperatorPhrase = phrase.trim();
            }
        }

        String canonicalPath;
        String valuesPart;
        String operatorCode;

        if (operatorIndex == -1) {
            canonicalPath = afterQuantifier;
            valuesPart    = "";
            operatorCode  = "EXISTS_AT_LEAST_ONE";
        } else {
            canonicalPath = afterQuantifier.substring(0, operatorIndex).trim();
            valuesPart    = afterQuantifier
                .substring(operatorIndex + matchedOperatorPhrase.length())
                .trim();
            operatorCode  = mapOperatorPhraseToCode(matchedOperatorPhrase);
        }

        // 3) MIDDLE – canonical path → entity/anchor/field/label
        DomainFieldDescriptor field = fieldResolver.resolve(canonicalPath);

        // 4) VALUES – often empty; in your real project codes may come from other cols
        List<String> values = parseValues(valuesPart);

        return new ParsedCondition(
            field.getEntityType(),
            field.getParentAnchorKey(),
            field.getFieldName(),
            operatorCode,
            values,
            field.getFieldTypeLabel(),
            role
        );
    }

    private String mapOperatorPhraseToCode(String phrase) {
        String p = phrase.toLowerCase().trim();

        if (p.equals("equals")) {
            return "==";
        }
        if (p.equals("must equals") || p.equals("must equal")) {
            return "==";
        }
        if (p.equals("must not equal") || p.equals("not equals")) {
            return "!=";
        }
        if (p.equals("must be one of")) {
            return "IN";
        }
        if (p.equals("must not be one of")) {
            return "NOT_IN";
        }
        return "UNKNOWN_OP";
    }

    private List<String> parseValues(String valuesPart) {
        if (valuesPart == null || valuesPart.isEmpty()) {
            return Collections.emptyList();
        }

        String cleaned = valuesPart
            .replace("[", "")
            .replace("]", "")
            .replace("\"", "")
            .replace("'", "")
            .trim();

        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = cleaned.split(",");
        List<String> values = new ArrayList<>();
        for (String token : tokens) {
            String v = token.trim();
            if (!v.isEmpty()) {
                values.add(v);
            }
        }
        return values;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/Constraint.java ====
package uk.gov.hmrc.rules.ir;

public class Constraint {

    private final String operator;
    private final Object value;

    public Constraint(String operator, Object value) {
        this.operator = operator;
        this.value = value;
    }

    public String getOperator() {
        return operator;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) value;
    }

    @Override
    public String toString() {
        return "Constraint{" +
               "operator='" + operator + '\'' +
               ", value=" + value +
               '}';
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/ConditionNode.java ====
package uk.gov.hmrc.rules.ir;

import uk.gov.hmrc.rules.parsing.ConditionRole;

public abstract class ConditionNode {

    private ConditionRole role = ConditionRole.OTHER;
    private String fieldTypeLabel;

    public ConditionRole getRole() {
        return role;
    }

    public void setRole(ConditionRole role) {
        this.role = role;
    }

    public String getFieldTypeLabel() {
        return fieldTypeLabel;
    }

    public void setFieldTypeLabel(String fieldTypeLabel) {
        this.fieldTypeLabel = fieldTypeLabel;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/ParentConditionNode.java ====
package uk.gov.hmrc.rules.ir;

import java.util.LinkedHashMap;
import java.util.Map;

public class ParentConditionNode extends ConditionNode {

    private String alias;
    private String factType;
    private final Map<String, Constraint> fieldConstraints = new LinkedHashMap<>();

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getFactType() {
        return factType;
    }

    public void setFactType(String factType) {
        this.factType = factType;
    }

    public Map<String, Constraint> getFieldConstraints() {
        return fieldConstraints;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/FactConditionNode.java ====
package uk.gov.hmrc.rules.ir;

import java.util.LinkedHashMap;
import java.util.Map;

public class FactConditionNode extends ConditionNode {

    private String alias;
    private String factType;
    private String parentAlias;
    private String parentField = "seq";
    private String parentJoinField = "parentSeq";

    private final Map<String, Constraint> fieldConstraints = new LinkedHashMap<>();

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getFactType() {
        return factType;
    }

    public void setFactType(String factType) {
        this.factType = factType;
    }

    public String getParentAlias() {
        return parentAlias;
    }

    public void setParentAlias(String parentAlias) {
        this.parentAlias = parentAlias;
    }

    public String getParentField() {
        return parentField;
    }

    public void setParentField(String parentField) {
        this.parentField = parentField;
    }

    public String getParentJoinField() {
        return parentJoinField;
    }

    public void setParentJoinField(String parentJoinField) {
        this.parentJoinField = parentJoinField;
    }

    public Map<String, Constraint> getFieldConstraints() {
        return fieldConstraints;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/ActionNode.java ====
package uk.gov.hmrc.rules.ir;

public abstract class ActionNode {
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/EmitErrorActionNode.java ====
package uk.gov.hmrc.rules.ir;

public class EmitErrorActionNode extends ActionNode {

    private String brCode;
    private String dmsErrorCode;

    public String getBrCode() {
        return brCode;
    }

    public void setBrCode(String brCode) {
        this.brCode = brCode;
    }

    public String getDmsErrorCode() {
        return dmsErrorCode;
    }

    public void setDmsErrorCode(String dmsErrorCode) {
        this.dmsErrorCode = dmsErrorCode;
    }

    @Override
    public String toString() {
        return "EmitErrorActionNode{" +
               "brCode='" + brCode + '\'' +
               ", dmsErrorCode='" + dmsErrorCode + '\'' +
               '}';
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/RuleModel.java ====
package uk.gov.hmrc.rules.ir;

import java.util.ArrayList;
import java.util.List;

public class RuleModel {

    private String id;
    private final List<ConditionNode> conditions = new ArrayList<>();
    private final List<ActionNode> actions = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ConditionNode> getConditions() {
        return conditions;
    }

    public List<ActionNode> getActions() {
        return actions;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/TopologyBuilder.java ====
package uk.gov.hmrc.rules.ir;

import uk.gov.hmrc.rules.parsing.ParsedCondition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TopologyBuilder {

    public static class ParentGroup {

        private final String anchorKey;
        private final String parentEntityType;
        private final List<ParsedCondition> children;

        public ParentGroup(String anchorKey,
                           String parentEntityType,
                           List<ParsedCondition> children) {
            this.anchorKey = anchorKey;
            this.parentEntityType = parentEntityType;
            this.children = children;
        }

        public String getAnchorKey() {
            return anchorKey;
        }

        public String getParentEntityType() {
            return parentEntityType;
        }

        public List<ParsedCondition> getChildren() {
            return children;
        }
    }

    public List<ParentGroup> build(List<ParsedCondition> parsedConditions) {
        Map<String, List<ParsedCondition>> byAnchor = new LinkedHashMap<>();

        for (ParsedCondition pc : parsedConditions) {
            byAnchor
                .computeIfAbsent(pc.getParentAnchorKey(),
                                 k -> new ArrayList<>())
                .add(pc);
        }

        List<ParentGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<ParsedCondition>> e : byAnchor.entrySet()) {
            String anchorKey = e.getKey();
            List<ParsedCondition> children = e.getValue();

            String parentType = inferParentType(children);
            result.add(new ParentGroup(anchorKey, parentType, children));
        }

        return result;
    }

    private String inferParentType(List<ParsedCondition> children) {
        // For now: always GoodsItem – you can refine per anchorKey
        return "GoodsItem";
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/ir/RuleIrGenerator.java ====
package uk.gov.hmrc.rules.ir;

import uk.gov.hmrc.rules.RuleRow;
import uk.gov.hmrc.rules.parsing.ConditionRole;
import uk.gov.hmrc.rules.parsing.ParsedCondition;

import java.util.List;

public class RuleIrGenerator {

    private final TopologyBuilder topologyBuilder = new TopologyBuilder();

    public RuleModel generate(RuleRow row, List<ParsedCondition> parsedConditions) {

        List<TopologyBuilder.ParentGroup> groups =
            topologyBuilder.build(parsedConditions);

        RuleModel model = new RuleModel();
        model.setId(row.id());

        int parentIndex = 1;
        int childIndex  = 1;

        for (TopologyBuilder.ParentGroup group : groups) {
            String parentAlias = "$gi" + parentIndex++;

            ParentConditionNode parentNode = new ParentConditionNode();
            parentNode.setAlias(parentAlias);
            parentNode.setFactType(group.getParentEntityType());
            parentNode.setRole(ConditionRole.OTHER);
            parentNode.setFieldTypeLabel(null);

            addHeaderConstraints(parentNode, row);
            model.getConditions().add(parentNode);

            for (ParsedCondition pc : group.getChildren()) {
                FactConditionNode child = new FactConditionNode();
                child.setAlias("$c" + childIndex++);
                child.setFactType(pc.getEntityType());
                child.setParentAlias(parentAlias);
                child.setRole(pc.getRole());
                child.setFieldTypeLabel(pc.getFieldTypeLabel());

                if (pc.getValues().isEmpty()) {
                    // EXISTS-only: presence of fact is enough
                } else if ("IN".equalsIgnoreCase(pc.getOperator())
                        || "NOT_IN".equalsIgnoreCase(pc.getOperator())) {
                    child.getFieldConstraints().put(
                        pc.getFieldName(),
                        new Constraint(pc.getOperator(), pc.getValues())
                    );
                } else {
                    child.getFieldConstraints().put(
                        pc.getFieldName(),
                        new Constraint(pc.getOperator(), pc.getValues().get(0))
                    );
                }

                model.getConditions().add(child);
            }
        }

        EmitErrorActionNode emit = new EmitErrorActionNode();
        emit.setBrCode(deriveBrCode(row));
        emit.setDmsErrorCode(row.errorCode());
        model.getActions().add(emit);

        return model;
    }

    private void addHeaderConstraints(ParentConditionNode parent, RuleRow row) {

        List<String> decTypes = row.declarationType();
        if (decTypes != null && !decTypes.isEmpty()) {
            if (decTypes.size() == 1) {
                parent.getFieldConstraints().put(
                    "declarationType",
                    new Constraint("==", decTypes.get(0))
                );
            } else {
                parent.getFieldConstraints().put(
                    "declarationType",
                    new Constraint("IN", decTypes)
                );
            }
        }

        List<String> procCats = row.procedureCategory();
        if (procCats != null && !procCats.isEmpty()) {
            if (procCats.size() == 1) {
                parent.getFieldConstraints().put(
                    "procedureCategory",
                    new Constraint("==", procCats.get(0))
                );
            } else {
                parent.getFieldConstraints().put(
                    "procedureCategory",
                    new Constraint("IN", procCats)
                );
            }
        }
    }

    private String deriveBrCode(RuleRow row) {
        String param = row.param();
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        String id = row.id();
        if (id == null) {
            return "";
        }
        int idx = id.indexOf('_');
        return (idx > 0) ? id.substring(0, idx) : id;
    }
}
==== FILE: rules-text-parser-ir-demo/src/main/java/uk/gov/hmrc/rules/demo/RuleIrSmokeTest.java ====
package uk.gov.hmrc.rules.demo;

import uk.gov.hmrc.rules.RuleRow;
import uk.gov.hmrc.rules.ir.ActionNode;
import uk.gov.hmrc.rules.ir.EmitErrorActionNode;
import uk.gov.hmrc.rules.ir.ConditionNode;
import uk.gov.hmrc.rules.ir.FactConditionNode;
import uk.gov.hmrc.rules.ir.ParentConditionNode;
import uk.gov.hmrc.rules.ir.RuleIrGenerator;
import uk.gov.hmrc.rules.ir.RuleModel;
import uk.gov.hmrc.rules.parsing.ConditionParser;
import uk.gov.hmrc.rules.parsing.ParsedCondition;
import uk.gov.hmrc.rules.parsing.TextConditionParser;

import java.util.List;

public class RuleIrSmokeTest {

    public static void main(String[] args) {

        ConditionParser parser = new TextConditionParser();
        RuleIrGenerator irGen = new RuleIrGenerator();

        List<RuleRow> rows = List.of(
            sampleSpAi(),
            sampleSpSpDifferentGi(),
            sampleAdDocAi()
        );

        for (RuleRow row : rows) {
            System.out.println("==================================================");
            System.out.println("RuleRow id  : " + row.id());
            System.out.println("IF   (excel): " + row.ifCondition());
            System.out.println("THEN (excel): " + row.thenCondition());
            System.out.println();

            ParsedCondition ifCond   = parser.parseIf(row.ifCondition());
            ParsedCondition thenCond = parser.parseThen(row.thenCondition());

            RuleModel model = irGen.generate(row, List.of(ifCond, thenCond));

            debugPrintParsedConditions(ifCond, thenCond);
            debugPrintIr(model);
            System.out.println();
        }
    }

    private static RuleRow sampleSpAi() {
        return new RuleRow(
            "BR675_1231",
            List.of("J", "F", "C"),
            List.of("C211", "C21E"),
            "there is at least one GoodsItem.specialProcedures.code equals 72M",
            "at least one GoodsItem.additionalInformation.code must equals MOVE3",
            "DMS12056",
            "BR675"
        );
    }

    private static RuleRow sampleSpSpDifferentGi() {
        return new RuleRow(
            "BR675_1125",
            List.of("all"),
            List.of("C211", "H1", "H2", "H3", "H4", "H5", "I1", "C21IEIDR"),
            "there is at least one GoodsItem.specialProcedures.code equals B02",
            "at least one GoodsItem.specialProcedures.code must be one of B03",
            "DMS12056",
            "BR675"
        );
    }

    private static RuleRow sampleAdDocAi() {
        return new RuleRow(
            "BR675_9999",
            List.of("all"),
            List.of("C211"),
            "there is at least one GoodsItem.additionalDocuments.type.code must equals AD1",
            "at least one GoodsItem.additionalInformation.code must equals INFO1",
            "DMS99999",
            "BR675"
        );
    }

    private static void debugPrintParsedConditions(ParsedCondition ifCond,
                                                   ParsedCondition thenCond) {
        System.out.println("Parsed IF condition:");
        debugPrintParsedCondition(ifCond);
        System.out.println();

        System.out.println("Parsed THEN condition:");
        debugPrintParsedCondition(thenCond);
        System.out.println();
    }

    private static void debugPrintParsedCondition(ParsedCondition pc) {
        System.out.println("  entityType      = " + pc.getEntityType());
        System.out.println("  parentAnchorKey = " + pc.getParentAnchorKey());
        System.out.println("  fieldName       = " + pc.getFieldName());
        System.out.println("  operator        = " + pc.getOperator());
        System.out.println("  values          = " + pc.getValues());
        System.out.println("  fieldTypeLabel  = " + pc.getFieldTypeLabel());
        System.out.println("  role            = " + pc.getRole());
    }

    private static void debugPrintIr(RuleModel model) {
        System.out.println("IR conditions:");
        for (ConditionNode node : model.getConditions()) {
            if (node instanceof ParentConditionNode parent) {
                System.out.println("  Parent: alias=" + parent.getAlias()
                    + ", factType=" + parent.getFactType()
                    + ", constraints=" + parent.getFieldConstraints());
            } else if (node instanceof FactConditionNode fact) {
                System.out.println("  Child:  alias=" + fact.getAlias()
                    + ", factType=" + fact.getFactType()
                    + ", parentAlias=" + fact.getParentAlias()
                    + ", role=" + fact.getRole()
                    + ", label=" + fact.getFieldTypeLabel()
                    + ", constraints=" + fact.getFieldConstraints());
            }
        }

        System.out.println("Actions:");
        for (ActionNode a : model.getActions()) {
            if (a instanceof EmitErrorActionNode emit) {
                System.out.println("  Emit BR=" + emit.getBrCode()
                    + ", DMS=" + emit.getDmsErrorCode());
            }
        }
    }
}
