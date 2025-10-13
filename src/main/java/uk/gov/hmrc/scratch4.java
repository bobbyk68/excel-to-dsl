package uk.gov.hmrc.dslgen.emit;

import java.util.List;

/* ============================== Writer ============================== */
class DslrFileWriter {
    private final StringBuffer buf = new StringBuffer();
    private boolean whenOpened = false, thenOpened = false;

    void whenLine(String line) {
        if (!whenOpened) { buf.append("[when]\n"); whenOpened = true; }
        buf.append("    ").append(line).append("\n");
    }
    void thenLine(String line) {
        if (!thenOpened) { buf.append("[then]\n"); thenOpened = true; }
        buf.append("    ").append(line).append("\n");
    }
    String getText() { return buf.toString(); }
    void reset() { buf.setLength(0); whenOpened = false; thenOpened = false; }
}

/* ============================== Model ============================== */
enum Operator { EQUALS, IN, NOT_IN }

final class Condition {
    private final String anchorKey;   // e.g., "GoodsItem.specialProcedures"
    private final String fieldKey;    // e.g., "specialProcedure.code"
    private final Operator operator;  // e.g., EQUALS
    private final String displayValue;// e.g., B02 or "\"B03\""

    Condition(String anchorKey, String fieldKey, Operator operator, String displayValue) {
        this.anchorKey = anchorKey; this.fieldKey = fieldKey; this.operator = operator; this.displayValue = displayValue;
    }
    String anchorKey()    { return anchorKey; }
    String fieldKey()     { return fieldKey; }
    Operator operator()   { return operator; }
    String displayValue() { return displayValue; }
}

final class ThenPart {
    private final String targetKey; // short key for error message routing
    ThenPart(String targetKey) { this.targetKey = targetKey; }
    String targetKey() { return targetKey; }
}

final class EmitContext {
    private final String anchorKey;                 // often "GoodsItem" (not used by hard-coded variants)
    private final java.util.List<Condition> conds;  // two IF conditions
    private final ThenPart thenPart;
    EmitContext(String anchorKey, java.util.List<Condition> conds, ThenPart thenPart) {
        this.anchorKey = anchorKey; this.conds = conds; this.thenPart = thenPart;
    }
    String anchorKey() { return anchorKey; }
    java.util.List<Condition> conditions() { return conds; }
    ThenPart thenPart() { return thenPart; }
}

/* ============================== SPI ============================== */
interface Emitter {
    int priority();
    boolean canHandle(EmitContext ctx);
    void emit(EmitContext ctx, DslrFileWriter dsl);
}

/* ============================== Emitters (hard-coded) ============================== */
class SpSpEmitter implements Emitter {
    @Override public int priority() { return 200; }
    @Override public boolean canHandle(EmitContext ctx) {
        if (ctx == null || ctx.conditions() == null || ctx.conditions().size() != 2) return false;
        Condition c1 = ctx.conditions().get(0), c2 = ctx.conditions().get(1);
        return "GoodsItem.specialProcedures".equals(c1.anchorKey())
                && "GoodsItem.specialProcedures".equals(c2.anchorKey())
                && "specialProcedure.code".equals(c1.fieldKey())
                && "specialProcedure.code".equals(c2.fieldKey());
    }
    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        Condition left  = ctx.conditions().get(0);
        Condition right = ctx.conditions().get(1);

        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code equals " + quote(left.displayValue()));

        dsl.whenLine("Matching goods item with special procedure exists");
        dsl.whenLine("- with code " + opWord(right.operator()) + " " + quote(right.displayValue()));

        dsl.thenLine("Emit BR675 validation error for special procedure");
    }
    private String opWord(Operator op) { return op == Operator.IN ? "in" : "equals"; }
    private String quote(String v) { if (v == null || v.isBlank()) return "\"\""; String s = v.trim();
        boolean q = (s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")); return q? s : "\""+s+"\""; }
}

class RpPpEmitter implements Emitter {
    @Override public int priority() { return 200; }
    @Override public boolean canHandle(EmitContext ctx) {
        if (ctx == null || ctx.conditions() == null || ctx.conditions().size() != 2) return false;
        Condition c1 = ctx.conditions().get(0), c2 = ctx.conditions().get(1);
        return "GoodsItem".equals(c1.anchorKey()) && "requestedProcedureCode".equals(c1.fieldKey())
                && "GoodsItem".equals(c2.anchorKey()) && "previousProcedureCode".equals(c2.fieldKey());
    }
    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        Condition rp = ctx.conditions().get(0), pp = ctx.conditions().get(1);
        dsl.whenLine("Goods item exists");
        dsl.whenLine("- with requested procedure code " + opWord(rp.operator()) + " " + quote(rp.displayValue()));
        dsl.whenLine("Matching goods item exists");
        dsl.whenLine("- with previous procedure code " + opWord(pp.operator()) + " " + quote(pp.displayValue()));
        dsl.thenLine("Emit BR675 validation error for requested and previous procedure");
    }
    private String opWord(Operator op) { switch (op) { case EQUALS:return "equals"; case IN:return "in"; case NOT_IN:return "not in"; default:return "?"; } }
    private String quote(String v) { if (v == null || v.isBlank()) return "\"\""; String s=v.trim();
        boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}

class SpAiEmitter implements Emitter {
    @Override public int priority() { return 200; }
    @Override public boolean canHandle(EmitContext ctx) {
        if (ctx == null || ctx.conditions() == null || ctx.conditions().size() != 2) return false;
        Condition c1 = ctx.conditions().get(0), c2 = ctx.conditions().get(1);
        return "GoodsItem.specialProcedures".equals(c1.anchorKey()) && "specialProcedure.code".equals(c1.fieldKey())
                && "GoodsItem.additionalInformation".equals(c2.anchorKey()) && "additionalInformation.code".equals(c2.fieldKey());
    }
    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        Condition sp = ctx.conditions().get(0), ai = ctx.conditions().get(1);
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code " + opWord(sp.operator()) + " " + quote(sp.displayValue()));
        dsl.whenLine("Matching goods item with additional information exists");
        dsl.whenLine("- with code " + opWord(ai.operator()) + " " + quote(ai.displayValue()));
        dsl.thenLine("Emit BR675 validation error for special procedure and additional information");
    }
    private String opWord(Operator op) { switch (op){case EQUALS:return "equals";case IN:return "in";case NOT_IN:return "not in";default:return "?";}}
    private String quote(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim(); boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}

class SpAdEmitter implements Emitter {
    @Override public int priority() { return 200; }
    @Override public boolean canHandle(EmitContext ctx) {
        if (ctx == null || ctx.conditions() == null || ctx.conditions().size() != 2) return false;
        Condition c1 = ctx.conditions().get(0), c2 = ctx.conditions().get(1);
        return "GoodsItem.specialProcedures".equals(c1.anchorKey()) && "specialProcedure.code".equals(c1.fieldKey())
                && "GoodsItem.additionalDocuments".equals(c2.anchorKey()) && "additionalDocuments.type.code".equals(c2.fieldKey());
    }
    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        Condition sp = ctx.conditions().get(0), ad = ctx.conditions().get(1);
        dsl.whenLine("Goods item with special procedure exists");
        dsl.whenLine("- with code " + opWord(sp.operator()) + " " + quote(sp.displayValue()));
        dsl.whenLine("Matching goods item with additional document exists");
        dsl.whenLine("- with type code " + opWord(ad.operator()) + " " + quote(ad.displayValue()));
        dsl.thenLine("Emit BR675 validation error for special procedure and additional document");
    }
    private String opWord(Operator op){switch(op){case EQUALS:return"equals";case IN:return"in";case NOT_IN:return"not in";default:return"?";}}
    private String quote(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim(); boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}

class RpSpEmitter implements Emitter {
    @Override public int priority() { return 200; }
    @Override public boolean canHandle(EmitContext ctx) {
        if (ctx == null || ctx.conditions() == null || ctx.conditions().size() != 2) return false;
        Condition c1 = ctx.conditions().get(0), c2 = ctx.conditions().get(1);
        return "GoodsItem".equals(c1.anchorKey()) && "requestedProcedureCode".equals(c1.fieldKey())
                && "GoodsItem.specialProcedures".equals(c2.anchorKey()) && "specialProcedure.code".equals(c2.fieldKey());
    }
    @Override public void emit(EmitContext ctx, DslrFileWriter dsl) {
        Condition rp = ctx.conditions().get(0), sp = ctx.conditions().get(1);
        dsl.whenLine("Goods item exists");
        dsl.whenLine("- with requested procedure code " + opWord(rp.operator()) + " " + quote(rp.displayValue()));
        dsl.whenLine("Matching goods item with special procedure exists");
        dsl.whenLine("- with code " + opWord(sp.operator()) + " " + quote(sp.displayValue()));
        dsl.thenLine("Emit BR675 validation error for requested procedure and special procedure");
    }
    private String opWord(Operator op){switch(op){case EQUALS:return"equals";case IN:return"in";default:return"?";}}
    private String quote(String v){ if(v==null||v.isBlank())return"\"\""; String s=v.trim(); boolean q=(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'")); return q? s : "\""+s+"\""; }
}

/* ============================== Registry + Demo ============================== */
final class EmitterRegistry {
    private final java.util.List<Emitter> emitters = new java.util.ArrayList<>();
    void register(Emitter e) { emitters.add(e); }
    void sortByPriorityDesc() { emitters.sort((a,b)->Integer.compare(b.priority(), a.priority())); }
    boolean dispatch(EmitContext ctx, DslrFileWriter dsl) {
        for (Emitter e: emitters) if (e.canHandle(ctx)) { e.emit(ctx, dsl); return true; }
        return false;
    }
}

public final class DemoHardCoded {
    public static void main(String[] args) {
        // 5 contexts (first 5 rows)
        EmitContext sp_sp = new EmitContext("GoodsItem",
                List.of(new Condition("GoodsItem.specialProcedures","specialProcedure.code",Operator.EQUALS,"B02"),
                        new Condition("GoodsItem.specialProcedures","specialProcedure.code",Operator.IN,"\"B03\"")),
                new ThenPart("specialProcedure"));

        EmitContext rp_pp = new EmitContext("GoodsItem",
                List.of(new Condition("GoodsItem","requestedProcedureCode",Operator.IN,"\"21\",\"51\""),
                        new Condition("GoodsItem","previousProcedureCode",Operator.NOT_IN,"\"00\",\"78\"")),
                new ThenPart("requestedAndPrevious"));

        EmitContext sp_ai = new EmitContext("GoodsItem",
                List.of(new Condition("GoodsItem.specialProcedures","specialProcedure.code",Operator.EQUALS,"C601"),
                        new Condition("GoodsItem.additionalInformation","additionalInformation.code",Operator.IN,"\"1234\",\"5678\"")),
                new ThenPart("spAndAi"));

        EmitContext sp_ad = new EmitContext("GoodsItem",
                List.of(new Condition("GoodsItem.specialProcedures","specialProcedure.code",Operator.EQUALS,"H1"),
                        new Condition("GoodsItem.additionalDocuments","additionalDocuments.type.code",Operator.IN,"\"C501\",\"C502\"")),
                new ThenPart("spAndAd"));

        EmitContext rp_sp = new EmitContext("GoodsItem",
                List.of(new Condition("GoodsItem","requestedProcedureCode",Operator.EQUALS,"40"),
                        new Condition("GoodsItem.specialProcedures","specialProcedure.code",Operator.IN,"\"I1\",\"C21I\"")),
                new ThenPart("requestedAndSpecial"));

        EmitterRegistry reg = new EmitterRegistry();
        reg.register(new SpSpEmitter());
        reg.register(new RpPpEmitter());
        reg.register(new SpAiEmitter());
        reg.register(new SpAdEmitter());
        reg.register(new RpSpEmitter());
        reg.sortByPriorityDesc();

        DslrFileWriter dsl = new DslrFileWriter();

        for (EmitContext ctx : List.of(sp_sp, rp_pp, sp_ai, sp_ad, rp_sp)) {
            dsl.reset();
            boolean ok = reg.dispatch(ctx, dsl);
            System.out.println("----");
            System.out.println(ok ? dsl.getText() : "[no emitter handled]");
        }
    }
}
