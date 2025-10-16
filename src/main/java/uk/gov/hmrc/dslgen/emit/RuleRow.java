package uk.gov.hmrc.dslgen.emit;

import java.util.*;
import java.util.regex.Pattern;

/* ---------- 0) Your existing RuleRow (adapt shape here only) ---------- */
final class RuleRow {
    private final String id;
    private final String ifLine1;     // first IF clause (PRIMARY)
    private final String ifLine2;     // second IF clause (MATCHING) — may be null/blank
    private final String thenLine;    // THEN clause — may be null/blank
    private final String errorCode;
    private final List<String> declarationType;
    private final List<String> procedureCategory;

    RuleRow(String id, String ifLine1, String ifLine2, String thenLine,
            String errorCode, List<String> declarationType, List<String> procedureCategory) {
        this.id = id;
        this.ifLine1 = ifLine1;
        this.ifLine2 = ifLine2;
        this.thenLine = thenLine;
        this.errorCode = errorCode;
        this.declarationType = declarationType;
        this.procedureCategory = procedureCategory;
    }
    String id()                { return id; }
    String ifLine1()           { return ifLine1; }
    String ifLine2()           { return ifLine2; }
    String thenLine()          { return thenLine; }
    String errorCode()         { return errorCode; }
    List<String> declarationType()  { return declarationType; }
    List<String> procedureCategory(){ return procedureCategory; }
}

/* ---------- 1) Minimal JSON entry + matcher ---------- */
final class JsonPhrase {
    final Pattern regex;   // MUST expose groups for (path, op, value)
    final String dsl;      // heading text to print (no dash)
    final boolean absence; // true if this pattern implies "no ..." / obligation (maps to NOT_EXISTS)

    JsonPhrase(Pattern regex, String dsl, boolean absence) {
        this.regex = regex; this.dsl = dsl; this.absence = absence;
    }
}

final class JsonRegexMatcher {
    private final List<JsonPhrase> phrases;
    JsonRegexMatcher(List<JsonPhrase> phrases) { this.phrases = phrases; }

    /** Try to match a line and build a MatchedHit (AtomicHit). Returns null on no match. */
    MatchedHit matchLine(String line, HitRole role) {
        if (line == null) return null;
        String norm = normalise(line);
        for (JsonPhrase p : phrases) {
            var m = p.regex.matcher(norm);
            if (m.matches()) {
                String path = safe(m, 1);                 // group 1: path (e.g., GoodsItem.additionalInformation.code)
                String op   = safe(m, 2);                 // group 2: equals|in|not in (if absence, we’ll override)
                String val  = safe(m, 3);                 // group 3: value
                // Map path → tokens
                var tokens = PathMapper.tokensFor(path);
                String opTok = p.absence ? "not exists" : op;
                Presence presence = p.absence ? Presence.ABSENT : Presence.PRESENT;

                return new MatchedHit.Builder()
                        .dslHeading(p.dsl)
                        .path(path)
                        .role(role)
                        .presence(presence)
                        .tokens(tokens.anchorTok, tokens.fieldTok, opTok, val)
                        .build();
            }
        }
        return null;
    }

    private static String normalise(String s) {
        // basic de-smartening + trim; keep dots intact to match JSON patterns with dots
        return s.replace('\u2013','-')
                .replace('\u2014','-')
                .replace('“','"')
                .replace('”','"')
                .replace('’','\'')
                .trim();
    }
    private static String safe(java.util.regex.Matcher m, int group) {
        try { String v = m.group(group); return v == null ? "" : v.trim(); } catch (Exception e) { return ""; }
    }
}

/* ---------- 2) Path→token map (centralised) ---------- */
final class PathMapper {
    static final class Tokens { final String anchorTok, fieldTok; Tokens(String a, String f){anchorTok=a; fieldTok=f;} }
    static Tokens tokensFor(String pathRaw) {
        if (pathRaw == null) return new Tokens("GI", "UNKNOWN");
        String p = pathRaw.trim().toLowerCase(Locale.ROOT);
        if (p.equals("goodsitem.specialprocedures.code") || p.equals("goodsitem.specialprocedure.code"))
            return new Tokens("SP", "SP_CODE");
        if (p.equals("goodsitem.additionalinformation.code"))
            return new Tokens("AI", "AI_CODE");
        if (p.equals("goodsitem.additionaldocuments.type.code"))
            return new Tokens("AD", "AD_TYPE_CODE");
        if (p.equals("goodsitem.requestedprocedurecode"))
            return new Tokens("GI", "REQ_PROC");
        if (p.equals("goodsitem.previousprocedurecode"))
            return new Tokens("GI", "PREV_PROC");
        // fallback: if it looks canonical already, pass through
        return new Tokens("GI", pathRaw);
    }
}

/* ---------- 3) Runner: RuleRow -> Hits -> EmitContext -> Emit ---------- */
final class Runner {

    private final JsonRegexMatcher matcher;
    private final EmitterRegistry registry;

    Runner(JsonRegexMatcher matcher, EmitterRegistry registry) {
        this.matcher = matcher;
        this.registry = registry;
    }

    /** Process one RuleRow and return the pretty DSLR text. */
    String process(RuleRow row) {
        // Step 2: create AtomicHits from JSON (PRIMARY & MATCHING)
        MatchedHit leftHit  = matcher.matchLine(row.ifLine1(), HitRole.PRIMARY);
        MatchedHit rightHit = matcher.matchLine(blankToNull(row.ifLine2()), HitRole.MATCHING);
        MatchedHit thenHit  = matcher.matchLine(blankToNull(row.thenLine()), HitRole.MATCHING); // may be absence/obligation

        // Step 3: shape (promotion + absence already encoded in hits via absence flag/op = "not exists")
        EmitContext ctx = EmitContextFactory.fromHits(leftHit, rightHit, thenHit);

        // Step 4: select emitter & render
        DslrFileWriter dsl = new DslrFileWriter();
        boolean handled = registry.dispatch(ctx, dsl);

        // Envelope (tweak to your rule template)
        return """
            rule "%s"
            @ErrorCode("%s")
            @declarationType("%s")
            @procedureCategory("%s")
            %s
            end
            """.formatted(
                row.id(),
                row.errorCode(),
                String.join(",", row.declarationType()),
                String.join(",", row.procedureCategory()),
                handled ? dsl.getText() : "[when]\n    [no emitter handled]\n"
            );
    }

    /** Process many rows (Excel sheet) */
    List<String> processAll(List<RuleRow> rows) {
        List<String> out = new ArrayList<>();
        for (RuleRow r : rows) out.add(process(r));
        return out;
    }

    private static String blankToNull(String s){ return (s == null || s.isBlank()) ? null : s; }
}

/* ---------- 4) Bootstrap example (wire matcher + registry) ---------- */
final class Bootstrap {
    static JsonRegexMatcher matcher() {
        // IMPORTANT: Your JSON should expose groups: (1) path, (2) op, (3) value
        // Below are example patterns; replace with your 349 JSON-driven entries.
        List<JsonPhrase> list = new ArrayList<>();

        // IF: there is at least one GoodsItem.specialProcedures.code equals 72M
        list.add(new JsonPhrase(
            Pattern.compile("^there\\s+is\\s+at\\s+least\\s+one\\s+(GoodsItem\\.specialProcedures\\.code)\\s+(equals|in|not\\s+in)\\s+(.+)$",
                            Pattern.CASE_INSENSITIVE),
            "Goods item with special procedure exists",
            false
        ));
        // IF: there is (no) GoodsItem.additionalInformation.code equals MOVE3 (absence if 'no' used)
        list.add(new JsonPhrase(
            Pattern.compile("^there\\s+is\\s+(?:no\\s+)?(GoodsItem\\.additionalInformation\\.code)\\s+(equals|in|not\\s+in)\\s+(.+)$",
                            Pattern.CASE_INSENSITIVE),
            "Matching goods item with additional information exists",
            false // absence will usually be modeled by a separate 'no ...' JSON; keep false here for clarity
        ));
        // THEN obligation form: at least one GoodsItem.additionalInformation.code must equals MOVE3  → ABSENT (NOT_EXISTS)
        list.add(new JsonPhrase(
            Pattern.compile("^at\\s+least\\s+one\\s+(GoodsItem\\.additionalInformation\\.code)\\s+(?:must\\s+)?equals\\s+(.+)$",
                            Pattern.CASE_INSENSITIVE),
            "Matching goods item with additional information exists",
            true // obligation → we want NOT_EXISTS on matching side to render "No matching ..."
        ));

        return new JsonRegexMatcher(list);
    }

    static EmitterRegistry registry() {
        EmitterRegistry reg = new EmitterRegistry();
        reg.register(new SpAiEmitter());
        reg.register(new DefaultFallbackEmitter()); // always last
        reg.sortByPriorityDesc();
        return reg;
    }
}

/* ---------- 5) Example: wire Runner with a couple of rows ---------- */
final class RunnerMain {
    public static void main(String[] args) {
        var matcher  = Bootstrap.matcher();
        var registry = Bootstrap.registry();
        var runner   = new Runner(matcher, registry);

        // Example rows
        RuleRow r1 = new RuleRow(
            "BR675_SP_AI_missing",
            "there is at least one GoodsItem.specialProcedures.code equals 72M", // IF #1
            null,                                                                // IF #2 none → THEN will be promoted
            "at least one GoodsItem.additionalInformation.code must equals MOVE3", // THEN obligation → ABSENT
            "DMS12056",
            List.of("J","F","C"),
            List.of("C211","C21E")
        );

        RuleRow r2 = new RuleRow(
            "SP_AI_present",
            "there is at least one GoodsItem.specialProcedures.code equals H7",
            "there is at least one GoodsItem.additionalInformation.code equals MOVEX", // both IFs present → no promotion
            null,
            "DMS00001",
            List.of("J"),
            List.of("C21E")
        );

        runner.processAll(List.of(r1, r2)).forEach(System.out::println);
    }
}
