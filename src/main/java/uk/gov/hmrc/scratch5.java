package uk.gov.hmrc.dslgen.emit;

import java.util.*;
import uk.gov.hmrc.dslgen.emit.adapter.AtomicHitAdapter;

/* ----------------------------- Minimal core model ----------------------------- */
enum Operator { EQUALS, IN, NOT_IN, EXISTS, NOT_EXISTS, GT, GTE, LT, LTE }

final class Condition {
    private final String anchorKey, fieldKey, displayValue;
    private final Operator operator;
    Condition(String anchorKey, String fieldKey, Operator operator, String displayValue) {
        this.anchorKey = anchorKey; this.fieldKey = fieldKey; this.operator = operator; this.displayValue = displayValue;
    }
    String anchorKey()    { return anchorKey; }
    String fieldKey()     { return fieldKey; }
    Operator operator()   { return operator; }
    String displayValue() { return displayValue; }
    // Optional: stable identity for dedupe
    String semanticKey() {
        return (anchorKey==null?"":anchorKey) + "|" + (fieldKey==null?"":fieldKey) + "|" +
                (operator==null?"?":operator.name()) + "|" + (displayValue==null?"":displayValue.trim());
    }
}

final class ThenPart {
    private final String targetKey;
    ThenPart(String targetKey) { this.targetKey = targetKey; }
    String targetKey() { return targetKey; }
}

final class EmitContext {
    private final String anchorKey;
    private final List<Condition> conditions;
    private final ThenPart thenPart;
    EmitContext(String anchorKey, List<Condition> conditions, ThenPart thenPart) {
        this.anchorKey = anchorKey; this.conditions = conditions; this.thenPart = thenPart;
    }
    String anchorKey() { return anchorKey; }
    List<Condition> conditions() { return conditions; }
    ThenPart thenPart() { return thenPart; }
}

interface Emitter {
    int priority();
    boolean canHandle(EmitContext ctx);
    void emit(EmitContext ctx, DslrFileWriter dsl);
}

final class DslrFileWriter {
    private final StringBuffer buf = new StringBuffer();
    private boolean whenOpened=false, thenOpened=false;
    void whenLine(String line){ if(!whenOpened){buf.append("[when]\n"); whenOpened=true;} buf.append("    ").append(line).append("\n"); }
    void thenLine(String line){ if(!thenOpened){buf.append("[then]\n"); thenOpened=true;} buf.append("    ").append(line).append("\n"); }
    String getText(){ return buf.toString(); }
    void reset(){ buf.setLength(0); whenOpened=false; thenOpened=false; }
}

/* ----------------------------- Registry ----------------------------- */
final class EmitterRegistry {
    private final List<Emitter> emitters = new ArrayList<>();
    void register(Emitter e){ emitters.add(e); }
    void sortByPriorityDesc(){ emitters.sort((a,b)->Integer.compare(b.priority(), a.priority())); }
    boolean dispatch(EmitContext ctx, DslrFileWriter dsl){
        for(Emitter e: emitters) if(e.canHandle(ctx)){ e.emit(ctx,dsl); return true; }
        return false;
    }
}

/* ----------------------------- Utilities ----------------------------- */
final class ConditionUtils {
    private ConditionUtils(){}
    static List<Condition> dedupe(List<Condition> in){
        if(in==null || in.isEmpty()) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Condition> out = new ArrayList<>();
        for(Condition c: in){
            String k = c.semanticKey();
            if(seen.add(k)) out.add(c);
        }
        return out;
    }
}

final class EmitContextFactory {
    private EmitContextFactory(){}
    static EmitContext fromPair(Condition left, Condition right, String thenTargetKey){
        List<Condition> conds = (right==null) ? List.of(left) : List.of(left, right);
        String anchorKey = parentAnchor(left.anchorKey());
        return new EmitContext(anchorKey, conds, new ThenPart(thenTargetKey));
    }
    private static String parentAnchor(String anchorKey){
        if(anchorKey!=null && anchorKey.startsWith("GoodsItem")) return "GoodsItem";
        return anchorKey!=null ? anchorKey : "Unknown";
    }
}

/* ----------------------------- Tiny parser stubs (replace with yours) ----------------------------- */
// These are just to make the demo compile/run. Use your real parser + AtomicHitAdapter.
interface ExcelRow { String leftAnchor(); String leftField(); String leftOp(); String leftVal();
    String rightAnchor(); String rightField(); String rightOp(); String rightVal();
    String thenToken(); /* e.g. "specialProcedure" */ }

final class AtomicHitParser {
    static AtomicHitAdapter.AtomicHit left(ExcelRow r){
        return new Hit(r.leftAnchor(), r.leftField(), r.leftOp(), r.leftVal());
    }
    static AtomicHitAdapter.AtomicHit right(ExcelRow r){
        return new Hit(r.rightAnchor(), r.rightField(), r.rightOp(), r.rightVal());
    }
    private record Hit(String a,String f,String o,String v) implements AtomicHitAdapter.AtomicHit {
        public String getAnchorToken(){ return a; }
        public String getFieldToken(){ return f; }
        public String getOperatorToken(){ return o; }
        public String getRawValue(){ return v; }
    }
}

/* ----------------------------- HARD-CODED emitters (first 5 rows) ----------------------------- */
class SpSpEmitter implements Emitter {
    public int priority(){ return 200; }
    public boolean canHandle(EmitContext ctx){
        if(ctx==null || ctx.conditions()==null || ctx.conditions().size()<1) return false;
        List<Condition> c = ctx.conditions();
        Condition c1 = c.get(0), c2 = c.size()>1? c.get(1): c.get(0);
        return "GoodsItem.specialProcedures".equals(c1.anchorKey())
                && "GoodsItem.specialProcedures".equals(c2.anchorKey())
                && "specialProcedure.code".equals(c1.fieldKey())
                && "specialProcedure.code".equals(c2.fieldKey());
    }
    public void emit(EmitContext ctx, DslrFileWriter dsl){
        List<Condition> unique = ConditionUtils.dedupe(ctx.conditions());
        Condition left = unique.get(0);
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code " + op(left) + " " + q(left.displayValue()));
        if(unique.size()>1){
            Condition right = unique.get(1);
            dsl.whenLine("Matching goods item with special procedure exists");
            dsl.whenLine("- with code " + op(right) + " " + q(right.displayValue()));
        }
        dsl.thenLine("Emit BR675 validation error for special procedure");
    }
    private String op(Condition c){ return c.operator()==Operator.IN ? "in" :
            c.operator()==Operator.NOT_IN ? "not in" : "equals"; }
    private String q(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim();
        boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}
class RpPpEmitter implements Emitter {
    public int priority(){ return 200; }
    public boolean canHandle(EmitContext ctx){
        if(ctx==null||ctx.conditions()==null||ctx.conditions().size()<2) return false;
        Condition c1=ctx.conditions().get(0), c2=ctx.conditions().get(1);
        return "GoodsItem".equals(c1.anchorKey()) && "requestedProcedureCode".equals(c1.fieldKey())
                && "GoodsItem".equals(c2.anchorKey()) && "previousProcedureCode".equals(c2.fieldKey());
    }
    public void emit(EmitContext ctx, DslrFileWriter dsl){
        Condition rp=ctx.conditions().get(0), pp=ctx.conditions().get(1);
        dsl.whenLine("Goods item exists");
        dsl.whenLine("- with requested procedure code " + op(rp) + " " + q(rp.displayValue()));
        dsl.whenLine("Matching goods item exists");
        dsl.whenLine("- with previous procedure code " + op(pp) + " " + q(pp.displayValue()));
        dsl.thenLine("Emit BR675 validation error for requested and previous procedure");
    }
    private String op(Condition c){ return c.operator()==Operator.IN?"in": c.operator()==Operator.NOT_IN?"not in":"equals"; }
    private String q(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim();
        boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}
class SpAiEmitter implements Emitter {
    public int priority(){ return 200; }
    public boolean canHandle(EmitContext ctx){
        if(ctx==null||ctx.conditions()==null||ctx.conditions().size()<2) return false;
        Condition c1=ctx.conditions().get(0), c2=ctx.conditions().get(1);
        return "GoodsItem.specialProcedures".equals(c1.anchorKey()) && "specialProcedure.code".equals(c1.fieldKey())
                && "GoodsItem.additionalInformation".equals(c2.anchorKey()) && "additionalInformation.code".equals(c2.fieldKey());
    }
    public void emit(EmitContext ctx, DslrFileWriter dsl){
        Condition sp=ctx.conditions().get(0), ai=ctx.conditions().get(1);
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code " + op(sp) + " " + q(sp.displayValue()));
        dsl.whenLine("Matching goods item with additional information exists");
        dsl.whenLine("- with code " + op(ai) + " " + q(ai.displayValue()));
        dsl.thenLine("Emit BR675 validation error for special procedure and additional information");
    }
    private String op(Condition c){ return c.operator()==Operator.IN?"in": c.operator()==Operator.NOT_IN?"not in":"equals"; }
    private String q(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim();
        boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}
class SpAdEmitter implements Emitter {
    public int priority(){ return 200; }
    public boolean canHandle(EmitContext ctx){
        if(ctx==null||ctx.conditions()==null||ctx.conditions().size()<2) return false;
        Condition c1=ctx.conditions().get(0), c2=ctx.conditions().get(1);
        return "GoodsItem.specialProcedures".equals(c1.anchorKey()) && "specialProcedure.code".equals(c1.fieldKey())
                && "GoodsItem.additionalDocuments".equals(c2.anchorKey()) && "additionalDocuments.type.code".equals(c2.fieldKey());
    }
    public void emit(EmitContext ctx, DslrFileWriter dsl){
        Condition sp=ctx.conditions().get(0), ad=ctx.conditions().get(1);
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code " + op(sp) + " " + q(sp.displayValue()));
        dsl.whenLine("Matching goods item with additional document exists");
        dsl.whenLine("- with type code " + op(ad) + " " + q(ad.displayValue()));
        dsl.thenLine("Emit BR675 validation error for special procedure and additional document");
    }
    private String op(Condition c){ return c.operator()==Operator.IN?"in": c.operator()==Operator.NOT_IN?"not in":"equals"; }
    private String q(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim();
        boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}
class RpSpEmitter implements Emitter {
    public int priority(){ return 200; }
    public boolean canHandle(EmitContext ctx){
        if(ctx==null||ctx.conditions()==null||ctx.conditions().size()<2) return false;
        Condition c1=ctx.conditions().get(0), c2=ctx.conditions().get(1);
        return "GoodsItem".equals(c1.anchorKey()) && "requestedProcedureCode".equals(c1.fieldKey())
                && "GoodsItem.specialProcedures".equals(c2.anchorKey()) && "specialProcedure.code".equals(c2.fieldKey());
    }
    public void emit(EmitContext ctx, DslrFileWriter dsl){
        Condition rp=ctx.conditions().get(0), sp=ctx.conditions().get(1);
        dsl.whenLine("Goods item exists");
        dsl.whenLine("- with requested procedure code " + op(rp) + " " + q(rp.displayValue()));
        dsl.whenLine("Matching goods item with special procedure exists");
        dsl.whenLine("- with code " + op(sp) + " " + q(sp.displayValue()));
        dsl.thenLine("Emit BR675 validation error for requested procedure and special procedure");
    }
    private String op(Condition c){ return c.operator()==Operator.IN?"in":"equals"; }
    private String q(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim();
        boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}

/* ----------------------------- Orchestration ----------------------------- */
public final class DslGenerationService {

    // choose which emitter set to use (true = hard-coded; false = phrasebook-driven if you add them)
    private final boolean useHardCoded = true;

    private EmitterRegistry registry(){
        EmitterRegistry reg = new EmitterRegistry();
        if (useHardCoded) {
            reg.register(new SpSpEmitter());
            reg.register(new RpPpEmitter());
            reg.register(new SpAiEmitter());
            reg.register(new SpAdEmitter());
            reg.register(new RpSpEmitter());
        } else {
            // register phrasebook variants here (same canHandle, no literals)
        }
        reg.sortByPriorityDesc();
        return reg;
    }

    /** THIS is where EmitContext is created: right after AtomicHit parsing. */
    public String generateForRow(ExcelRow row){
        // 1) Parse to AtomicHit
        AtomicHitAdapter.AtomicHit leftHit  = AtomicHitParser.left(row);
        AtomicHitAdapter.AtomicHit rightHit = AtomicHitParser.right(row);

        // 2) Convert ASAP to canonical Condition
        Condition left  = AtomicHitAdapter.toCondition(leftHit);
        Condition right = AtomicHitAdapter.toCondition(rightHit);

        // 3) Optional dedupe
        List<Condition> conds = ConditionUtils.dedupe(List.of(left, right));

        // 4) Build EmitContext (note: target key comes from row.thenToken)
        String thenTargetKey = row.thenToken() != null ? row.thenToken() : "validation";
        EmitContext ctx = new EmitContext("GoodsItem", conds, new ThenPart(thenTargetKey));

        // 5) Dispatch to emitter
        DslrFileWriter dsl = new DslrFileWriter();
        boolean handled = registry().dispatch(ctx, dsl);
        return handled ? dsl.getText() : "[no emitter handled]";
    }

    /* --- Tiny demo harness --- */
    public static void main(String[] args){
        DslGenerationService svc = new DslGenerationService();

        // Example rows (replace with your real rows)
        ExcelRow sp_sp = new ExcelRow() {
            public String leftAnchor(){return "SP";} public String leftField(){return "SP_CODE";}
            public String leftOp(){return "equals";} public String leftVal(){return "B02";}
            public String rightAnchor(){return "SP";} public String rightField(){return "SP_CODE";}
            public String rightOp(){return "in";} public String rightVal(){return "\"B03\"";}
            public String thenToken(){return "specialProcedure";}
        };
        ExcelRow rp_pp = new ExcelRow() {
            public String leftAnchor(){return "RP";} public String leftField(){return "REQ_PROC";}
            public String leftOp(){return "in";} public String leftVal(){return "\"21\",\"51\"";}
            public String rightAnchor(){return "PP";} public String rightField(){return "PREV_PROC";}
            public String rightOp(){return "not in";} public String rightVal(){return "\"00\",\"78\"";}
            public String thenToken(){return "requestedAndPrevious";}
        };

        System.out.println("---- SP/SP ----");
        System.out.println(svc.generateForRow(sp_sp));
        System.out.println("---- RP/PP ----");
        System.out.println(svc.generateForRow(rp_pp));
    }
}
