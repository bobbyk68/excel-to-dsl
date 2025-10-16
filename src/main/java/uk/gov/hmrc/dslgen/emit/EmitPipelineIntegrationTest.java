package uk.gov.hmrc.dslgen.emit;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test: Excel 2-col rows (ifCondition + thenCondition) → DSLR.
 * We assert the full rule output for 7 representative combinations.
 */
public class EmitPipelineIntegrationTest {

    /* ============================== Domain stubs & helpers ============================== */

    enum Operator { EQUALS, IN, NOT_IN, EXISTS, NOT_EXISTS }

    static final class RuleRow {
        final String id;
        final String ifCondition;    // Excel col 1
        final String thenCondition;  // Excel col 2 (your SECOND IF)
        final String errorCode;
        final List<String> declarationType;
        final List<String> procedureCategory;
        RuleRow(String id, String ifCondition, String thenCondition,
                String errorCode, List<String> declarationType, List<String> procedureCategory) {
            this.id=id; this.ifCondition=ifCondition; this.thenCondition=thenCondition;
            this.errorCode=errorCode; this.declarationType=declarationType; this.procedureCategory=procedureCategory;
        }
    }

    record RuleMeta(String ruleName, String errorCode, List<String> declarationTypes, List<String> procedureCategories) {
        static RuleMeta of(RuleRow r) {
            return new RuleMeta(r.id, r.errorCode, r.declarationType, r.procedureCategory);
        }
    }

    static final class DslrFileWriter {
        private final StringBuilder buf = new StringBuilder();
        private boolean when=false, then=false;
        void beginRule(RuleMeta m) {
            buf.append("rule \"").append(m.ruleName()).append("\"\n");
            buf.append("@ErrorCode(\"").append(m.errorCode()).append("\")\n");
            buf.append("@declarationType(\"").append(String.join(",", m.declarationTypes())).append("\")\n");
            buf.append("@procedureCategory(\"").append(String.join(",", m.procedureCategories())).append("\")\n");
        }
        void whenLine(String s){ if(!when){buf.append("[when]\n"); when=true;} buf.append("    ").append(s).append("\n"); }
        void thenLine(String s){ if(!then){buf.append("[then]\n"); then=true;} buf.append("    ").append(s).append("\n"); }
        void endRule(){ buf.append("end\n"); }
        String text(){ return buf.toString(); }
        void reset(){ buf.setLength(0); when=false; then=false; }
    }

    interface Emitter { int priority(); boolean canHandle(EmitContext ctx); void emit(EmitContext ctx, DslrFileWriter dsl); }

    static final class EmitterRegistry {
        private final List<Emitter> list = new ArrayList<>();
        void register(Emitter e){ list.add(e); }
        void sort(){ list.sort((a,b)->Integer.compare(b.priority(), a.priority())); }
        boolean dispatch(EmitContext ctx, DslrFileWriter dsl){
            for (Emitter e: list) if (e.canHandle(ctx)) { e.emit(ctx,dsl); return true; }
            return false;
        }
    }

    /* ============================== Matching layer ============================== */

    // Minimal AtomicHit interface used by the adapter
    interface AtomicHit {
        String getAnchorToken();   // SP / AI / AD / GI
        String getFieldToken();    // SP_CODE / AI_CODE / AD_TYPE_CODE / REQ_PROC / PREV_PROC
        String getOperatorToken(); // equals / in / not in / not exists
        String getRawValue();      // UE / 72M / A1,B2 / etc.
    }

    static final class Hit implements AtomicHit {
        String anchorTok, fieldTok, operatorTok, rawVal;
        @Override public String getAnchorToken(){ return nz(anchorTok); }
        @Override public String getFieldToken(){ return nz(fieldTok); }
        @Override public String getOperatorToken(){ return nz(operatorTok); }
        @Override public String getRawValue(){ return nz(rawVal); }
        Hit setTokens(String a, String f, String o, String v){ this.anchorTok=a; this.fieldTok=f; this.operatorTok=o; this.rawVal=v; return this; }
        private static String nz(String s){ return s==null? "": s; }
    }

    static final class PathMapper {
        static final class Tokens { final String anchorTok, fieldTok; Tokens(String a,String f){anchorTok=a; fieldTok=f;} }
        static Tokens tokensFor(String pathRaw){
            if (pathRaw==null) return new Tokens("GI","UNKNOWN");
            String p = pathRaw.trim().toLowerCase(Locale.ROOT);
            if (p.equals("goodsitem.specialprocedures.code")||p.equals("goodsitem.specialprocedure.code")) return new Tokens("SP","SP_CODE");
            if (p.equals("goodsitem.additionalinformation.code")) return new Tokens("AI","AI_CODE");
            if (p.equals("goodsitem.additionaldocuments.type.code")) return new Tokens("AD","AD_TYPE_CODE");
            if (p.equals("goodsitem.requestedprocedurecode")) return new Tokens("GI","REQ_PROC");
            if (p.equals("goodsitem.previousprocedurecode"))  return new Tokens("GI","PREV_PROC");
            return new Tokens("GI", pathRaw);
        }
    }

    static final class AtomicHitAdapter {
        private static final Map<String,String> ANCHOR = Map.of(
                "SP","GoodsItem.specialProcedures",
                "AI","GoodsItem.additionalInformation",
                "AD","GoodsItem.additionalDocuments",
                "GI","GoodsItem"
        );
        private static final Map<String,String> FIELD = Map.ofEntries(
                Map.entry("SP_CODE","specialProcedure.code"),
                Map.entry("AI_CODE","additionalInformation.code"),
                Map.entry("AD_TYPE_CODE","additionalDocuments.type.code"),
                Map.entry("REQ_PROC","requestedProcedureCode"),
                Map.entry("PREV_PROC","previousProcedureCode")
        );
        static Condition toCondition(AtomicHit hit){
            String anchor = ANCHOR.getOrDefault(hit.getAnchorToken(), "GoodsItem");
            String field  = FIELD.getOrDefault(hit.getFieldToken(), hit.getFieldToken());
            Operator op   = switch (hit.getOperatorToken().toLowerCase(Locale.ROOT)) {
                case "in" -> Operator.IN; case "not in" -> Operator.NOT_IN; case "not exists" -> Operator.NOT_EXISTS; default -> Operator.EQUALS;
            };
            String val = normaliseValue(op, hit.getRawValue());
            return new Condition(anchor, field, op, val);
        }
        private static String normaliseValue(Operator op, String raw){
            if (op==Operator.NOT_EXISTS) return quote(raw);
            if (raw==null||raw.isBlank()) return "\"\"";
            String s=raw.trim();
            if (s.contains(",") && (s.contains("\"")||s.contains("'"))) return s;
            if (s.contains(",")) { String[] p=s.split(","); for(int i=0;i<p.length;i++) p[i]=quote(p[i].trim()); return String.join(",",p); }
            return quote(s);
        }
        private static String quote(String v){ if (v==null||v.isBlank()) return "\"\""; String s=v.trim(); boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s: "\""+s+"\""; }
    }

    static final class Condition {
        final String anchorKey, fieldKey, displayValue; final Operator operator;
        Condition(String a,String f,Operator o,String v){ anchorKey=a; fieldKey=f; operator=o; displayValue=v; }
        String semanticKey(){ return anchorKey+"|"+fieldKey+"|"+operator+"|"+(displayValue==null?"":displayValue.trim()); }
    }

    static final class EmitContext {
        final String parentAnchor; final List<Condition> conditions; final ThenPart thenPart;
        EmitContext(String p, List<Condition> c, ThenPart t){ parentAnchor=p; conditions=c; thenPart=t; }
    }
    static final class ThenPart { final String key; ThenPart(String k){ key=k; } }

    static final class Shaper {
        static EmitContext shape(AtomicHit leftHit, AtomicHit rightHit) {
            Condition left  = AtomicHitAdapter.toCondition(leftHit);
            Condition right = rightHit != null ? AtomicHitAdapter.toCondition(rightHit) : null;
            List<Condition> conds = new ArrayList<>(2); conds.add(left); if (right!=null) conds.add(right);
            conds = dedupe(conds);
            String parent = deriveParent(conds);
            String key = inferKey(conds);
            return new EmitContext(parent, conds, new ThenPart(key));
        }
        private static List<Condition> dedupe(List<Condition> in){ LinkedHashSet<String> seen=new LinkedHashSet<>(); List<Condition> out=new ArrayList<>(); for(Condition c:in){ if(seen.add(c.semanticKey())) out.add(c);} return out; }
        private static String deriveParent(List<Condition> c){ boolean all=c.stream().allMatch(x->x.anchorKey!=null && x.anchorKey.startsWith("GoodsItem")); return all? "GoodsItem": (c.get(0).anchorKey==null? "Unknown": c.get(0).anchorKey); }
        private static String inferKey(List<Condition> c){
            boolean hasSP = c.stream().anyMatch(x->"specialProcedure.code".equals(x.fieldKey));
            boolean hasAI = c.stream().anyMatch(x->"additionalInformation.code".equals(x.fieldKey));
            boolean hasAD = c.stream().anyMatch(x->"additionalDocuments.type.code".equals(x.fieldKey));
            boolean hasRP = c.stream().anyMatch(x->"requestedProcedureCode".equals(x.fieldKey));
            boolean hasPP = c.stream().anyMatch(x->"previousProcedureCode".equals(x.fieldKey));
            if (hasSP&&hasAI) return "spAndAi";
            if (hasSP&&hasAD) return "spAndAd";
            if (hasRP&&hasPP) return "requestedAndPrevious";
            if (hasSP) return "specialProcedure";
            return "validation";
        }
    }

    /* ============================== Emitters ============================== */

    static final class SpAiEmitter implements Emitter {
        public int priority(){ return 200; }
        public boolean canHandle(EmitContext ctx){
            if (ctx==null||ctx.conditions==null||ctx.conditions.size()<2) return false;
            Condition L=ctx.conditions.get(0), R=ctx.conditions.get(1);
            return L.anchorKey.startsWith("GoodsItem.specialProcedures")
                && R.anchorKey.startsWith("GoodsItem.additionalInformation")
                && "specialProcedure.code".equals(L.fieldKey)
                && "additionalInformation.code".equals(R.fieldKey);
        }
        public void emit(EmitContext ctx, DslrFileWriter dsl){
            Condition L=ctx.conditions.get(0), R=ctx.conditions.get(1);
            boolean absent = R.operator==Operator.NOT_EXISTS;
            dsl.whenLine("Goods item with special procedure exists");
            dsl.whenLine("- with code equals " + L.displayValue);
            dsl.whenLine(absent? "No matching goods item with additional information exists"
                               : "Matching goods item with additional information exists");
            Operator show = absent? Operator.EQUALS : R.operator;
            dsl.whenLine("- with code " + (show==Operator.NOT_IN? "not in": show==Operator.IN? "in":"equals") + " " + R.displayValue);
            dsl.thenLine(absent? "Emit BR675 validation error for special procedure and no additional information"
                               : "Emit BR675 validation error for special procedure and additional information");
        }
    }

    static final class DefaultFallbackEmitter implements Emitter {
        public int priority(){ return 0; }
        public boolean canHandle(EmitContext ctx){ return ctx!=null && ctx.conditions!=null && !ctx.conditions.isEmpty(); }
        public void emit(EmitContext ctx, DslrFileWriter dsl){
            Condition c1 = ctx.conditions.get(0);
            dsl.whenLine(primary(c1.anchorKey));
            dsl.whenLine("- with " + field(c1.fieldKey) + " " + op(c1.operator) + " " + c1.displayValue);
            if (ctx.conditions.size()>1){
                Condition c2 = ctx.conditions.get(1);
                boolean absent = c2.operator==Operator.NOT_EXISTS;
                dsl.whenLine(absent? "No matching "+noun(c2.anchorKey)+" exists" : "Matching "+noun(c2.anchorKey)+" exists");
                Operator show = absent? Operator.EQUALS : c2.operator;
                dsl.whenLine("- with " + field(c2.fieldKey) + " " + op(show) + " " + c2.displayValue);
            }
            dsl.thenLine("Emit BR675 validation error");
        }
        private String primary(String a){ if(a!=null&&a.startsWith("GoodsItem.specialProcedures")) return "Goods item with special procedure exists"; if(a!=null&&a.startsWith("GoodsItem.additionalInformation")) return "Goods item with additional information exists"; if(a!=null&&a.startsWith("GoodsItem.additionalDocuments")) return "Goods item with additional document exists"; return "Goods item exists"; }
        private String noun(String a){ if(a!=null&&a.startsWith("GoodsItem.specialProcedures")) return "goods item with special procedure"; if(a!=null&&a.startsWith("GoodsItem.additionalInformation")) return "goods item with additional information"; if(a!=null&&a.startsWith("GoodsItem.additionalDocuments")) return "goods item with additional document"; return "goods item"; }
        private String field(String k){ return switch (k){ case "specialProcedure.code","additionalInformation.code"->"code"; case "additionalDocuments.type.code"->"type code"; case "requestedProcedureCode"->"requested procedure code"; case "previousProcedureCode"->"previous procedure code"; default->k; }; }
        private String op(Operator o){ return switch (o){ case IN->"in"; case NOT_IN->"not in"; default->"equals"; }; }
    }

    /* ============================== JSON matcher (enough patterns to cover tests) ============================== */

    static final class JsonPhrase { final Pattern regex; final boolean absence; JsonPhrase(Pattern r, boolean a){ regex=r; absence=a; } }
    static final class JsonRegexMatcher {
        final List<JsonPhrase> phrases;
        JsonRegexMatcher(List<JsonPhrase> p){ phrases=p; }

        Hit matchIf(String literal){ return match(literal, false); }
        Hit matchThen(String literal){ return match(literal, true); } // your second IF → absence may apply

        private Hit match(String literal, boolean secondIf) {
            if (literal==null || literal.isBlank()) return null;
            String norm = normalise(literal);
            for (JsonPhrase p : phrases) {
                var m = p.regex.matcher(norm);
                if (m.matches()) {
                    String path = m.group(1).trim();
                    String op   = m.group(2).trim().toLowerCase(Locale.ROOT);
                    String val  = m.group(3).trim();
                    // normalise operator synonyms
                    if (op.equals("is one of")) op = "in";
                    if (op.equals("is not one of")) op = "not in";
                    // absence only applies to the second IF (your thenCondition) AND if the template encodes obligation
                    boolean absent = secondIf && p.absence;
                    String opTok = absent ? "not exists" : op;
                    var tk = PathMapper.tokensFor(path);
                    return new Hit().setTokens(tk.anchorTok, tk.fieldTok, opTok, val);
                }
            }
            throw new IllegalStateException("No JSON pattern matched: " + literal);
        }

        private static String normalise(String s){
            return s.replace('\u2013','-').replace('\u2014','-')
                    .replace('“','"').replace('”','"').replace('’','\'').trim();
        }
    }

    private static JsonRegexMatcher buildMatcher() {
        List<JsonPhrase> list = new ArrayList<>();
        // existence: there is at least one <path> <op> <val>
        list.add(new JsonPhrase(Pattern.compile("^there\\s+is\\s+at\\s+least\\s+one\\s+(GoodsItem\\.[A-Za-z\\.]+)\\s+(equals|in|not\\s+in|is\\s+one\\s+of|is\\s+not\\s+one\\s+of)\\s+(.+)$", Pattern.CASE_INSENSITIVE), false));
        // obligation/absence form (your “thenCondition” style): at least one <path> must <op> <val>
        list.add(new JsonPhrase(Pattern.compile("^at\\s+least\\s+one\\s+(GoodsItem\\.[A-Za-z\\.]+)\\s+(?:must\\s+)?(equals|in|not\\s+in|is\\s+one\\s+of|is\\s+not\\s+one\\s+of)\\s+(.+)$", Pattern.CASE_INSENSITIVE), true));
        // also allow second clause to be written like "there is no <path> equals <val>"
        list.add(new JsonPhrase(Pattern.compile("^there\\s+is\\s+no\\s+(GoodsItem\\.[A-Za-z\\.]+)\\s+(equals|in|not\\s+in|is\\s+one\\s+of|is\\s+not\\s+one\\s+of)\\s+(.+)$", Pattern.CASE_INSENSITIVE), true));
        return new JsonRegexMatcher(list);
    }

    /* ============================== Test ============================== */

    @Test
    void seven_rows_end_to_end() {
        JsonRegexMatcher matcher = buildMatcher();

        EmitterRegistry reg = new EmitterRegistry();
        reg.register(new SpAiEmitter());
        reg.register(new DefaultFallbackEmitter());
        reg.sort();

        // 7 RuleRows covering common pairs & presence/absence
        List<RuleRow> rows = List.of(
            // 1) SP + AI (present)
            new RuleRow("R1_SP_AI_present",
                "there is at least one GoodsItem.specialProcedures.code equals UE",
                "there is at least one GoodsItem.additionalInformation.code equals MOVE3",
                "DMS10001", List.of("J","F"), List.of("C211")),
            // 2) SP + AI (AI absent via 'must equals' → NOT_EXISTS on right)
            new RuleRow("R2_SP_AI_absent",
                "there is at least one GoodsItem.specialProcedures.code equals 72M",
                "at least one GoodsItem.additionalInformation.code must equals MOVE3",
                "DMS12056", List.of("J","F","C"), List.of("C211","C21E")),
            // 3) SP + AD (present) → fallback emitter
            new RuleRow("R3_SP_AD_present",
                "there is at least one GoodsItem.specialProcedures.code equals H7",
                "there is at least one GoodsItem.additionalDocuments.type.code equals A1",
                "DMS20000", List.of("F"), List.of("C21E")),
            // 4) RP + PP (present) → fallback emitter
            new RuleRow("R4_RP_PP",
                "there is at least one GoodsItem.requestedProcedureCode equals 00",
                "there is at least one GoodsItem.previousProcedureCode equals 10",
                "DMS30000", List.of("C"), List.of("C211")),
            // 5) SP + SP (present) → fallback emitter, two SP criteria
            new RuleRow("R5_SP_SP",
                "there is at least one GoodsItem.specialProcedures.code equals 31",
                "there is at least one GoodsItem.specialProcedures.code equals 32",
                "DMS40000", List.of("J"), List.of("C211")),
            // 6) AI only (second IF empty) → fallback single condition
            new RuleRow("R6_AI_only",
                "there is at least one GoodsItem.additionalInformation.code equals MOVEX",
                "",
                "DMS50000", List.of("J"), List.of("C211")),
            // 7) Unknown path → fallback, still prints “Goods item exists”
            new RuleRow("R7_Unknown",
                "there is at least one GoodsItem.unknownField equals Z9",
                "there is at least one GoodsItem.additionalInformation.code equals AAA",
                "DMS60000", List.of("J"), List.of("C211"))
        );

        // Run pipeline & assert outputs
        for (RuleRow row : rows) {
            DslrFileWriter dsl = new DslrFileWriter();
            RuleMeta meta = RuleMeta.of(row);
            dsl.beginRule(meta);

            // IF #1 (PRIMARY) and IF #2 (MATCHING = your "thenCondition")
            Hit leftHit  = matcher.matchIf(emptyToNull(row.ifCondition));
            Hit rightHit = emptyToNull(row.thenCondition) == null ? null : matcher.matchThen(row.thenCondition);

            // Shape & dispatch
            EmitContext ctx = Shaper.shape(leftHit, rightHit);
            reg.dispatch(ctx, dsl);
            dsl.endRule();

            String actual = dsl.text();

            // expected snapshots
            String expected = switch (row.id) {
                case "R1_SP_AI_present" -> """
                    rule "R1_SP_AI_present"
                    @ErrorCode("DMS10001")
                    @declarationType("J,F")
                    @procedureCategory("C211")
                    [when]
                        Goods item with special procedure exists
                        - with code equals "UE"
                        Matching goods item with additional information exists
                        - with code equals "MOVE3"
                    [then]
                        Emit BR675 validation error for special procedure and additional information
                    end
                    """;
                case "R2_SP_AI_absent" -> """
                    rule "R2_SP_AI_absent"
                    @ErrorCode("DMS12056")
                    @declarationType("J,F,C")
                    @procedureCategory("C211,C21E")
                    [when]
                        Goods item with special procedure exists
                        - with code equals "72M"
                        No matching goods item with additional information exists
                        - with code equals "MOVE3"
                    [then]
                        Emit BR675 validation error for special procedure and no additional information
                    end
                    """;
                case "R3_SP_AD_present" -> """
                    rule "R3_SP_AD_present"
                    @ErrorCode("DMS20000")
                    @declarationType("F")
                    @procedureCategory("C21E")
                    [when]
                        Goods item with special procedure exists
                        - with code equals "H7"
                        Matching goods item with additional document exists
                        - with type code equals "A1"
                    [then]
                        Emit BR675 validation error
                    end
                    """;
                case "R4_RP_PP" -> """
                    rule "R4_RP_PP"
                    @ErrorCode("DMS30000")
                    @declarationType("C")
                    @procedureCategory("C211")
                    [when]
                        Goods item exists
                        - with requested procedure code equals "00"
                        Matching goods item exists
                        - with previous procedure code equals "10"
                    [then]
                        Emit BR675 validation error
                    end
                    """;
                case "R5_SP_SP" -> """
                    rule "R5_SP_SP"
                    @ErrorCode("DMS40000")
                    @declarationType("J")
                    @procedureCategory("C211")
                    [when]
                        Goods item with special procedure exists
                        - with code equals "31"
                        Matching goods item with special procedure exists
                        - with code equals "32"
                    [then]
                        Emit BR675 validation error
                    end
                    """;
                case "R6_AI_only" -> """
                    rule "R6_AI_only"
                    @ErrorCode("DMS50000")
                    @declarationType("J")
                    @procedureCategory("C211")
                    [when]
                        Goods item with additional information exists
                        - with code equals "MOVEX"
                    [then]
                        Emit BR675 validation error
                    end
                    """;
                case "R7_Unknown" -> """
                    rule "R7_Unknown"
                    @ErrorCode("DMS60000")
                    @declarationType("J")
                    @procedureCategory("C211")
                    [when]
                        Goods item exists
                        - with unknownField equals "Z9"
                        Matching goods item with additional information exists
                        - with code equals "AAA"
                    [then]
                        Emit BR675 validation error
                    end
                    """;
                default -> throw new IllegalStateException("No expected snapshot for " + row.id);
            };

            assertEquals(normalise(expected), normalise(actual), "Mismatch for rule " + row.id);
        }
    }

    /* ============================== Utils ============================== */

    private static String emptyToNull(String s){ return (s==null||s.isBlank())? null: s; }
    private static String normalise(String s){ return s.replace("\r\n","\n"); }
}
