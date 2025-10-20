// ===============================================
// === [A] ADD THESE TYPE+HELPERS NEAR THE TOP ===
// ===============================================
// Put these in your Runner (or the class that owns collectAll/emit) with your other static helpers.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

static final class Impact {
    // Cohort container: preserves insertion order and 1:1 mapping (ruleId -> errorCode)
    final LinkedHashMap<String,String> idToErr = new LinkedHashMap<>();
    void add(String ruleId, String err) { idToErr.putIfAbsent(ruleId, err); }
}

record KeyV1(String combo, String v1) {}
record KeyPair(String combo, String pairKey) {}
record Candidate(String ruleId, String err, AllowedSpec spec) {}
record Buf(String comboN, String ruleId, String err, String v1N, String v2N, String pairKey, String rightClause) {}

static final class AllowedSpec {
    final boolean isNegated;          // true for "not in", "is not one of", "!="
    final LinkedHashSet<String> items;// normalised tokens, e.g., [D02, D03, D04]
    AllowedSpec(boolean n, Collection<String> i){ this.isNegated=n; this.items=new LinkedHashSet<>(i); }
}

// ---- Normalisation + keying ----
private static String normCombo(String s){ return s==null? "NA-NA" : s.trim().toUpperCase(); }
private static String normVal(String s){ return s==null? "" : s.trim().toUpperCase(); }
private static String canonicalPair(String a, String b){
    String v1 = normVal(a), v2 = normVal(b);
    if (v1.isBlank() || v2.isBlank()) return null;
    return (v1.compareTo(v2) <= 0) ? v1 + "|" + v2 : v2 + "|" + v1; // order-independent key
}

// ---- Clause parsing for unquoted lists + negation ----
// Handles: equals / in / one of / NOT variants; unquoted CSV/pipe/space; falls back to a single token.
private static AllowedSpec parseAllowedSpec(String clauseText, String fallbackSingle) {
    String t = clauseText == null ? "" : clauseText.trim();
    boolean neg = Pattern.compile("\\b(is\\s+not\\s+one\\s+of|not\\s+one\\s+of|not\\s+in|!=|<>)\\b",
            Pattern.CASE_INSENSITIVE).matcher(t).find();

    LinkedHashSet<String> out = new LinkedHashSet<>();

    Matcher m = Pattern.compile(
            "(?:is\\s+not\\s+one\\s+of|not\\s+one\\s+of|not\\s+in|one\\s+of|in|equals|==|!=|<>)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE).matcher(t);
    if (m.find()) {
        String rhs = m.group(1).trim().replaceAll("^\\[|\\]$", "");
        for (String tok : rhs.split("[,|\\s]+")) {
            tok = tok.trim();
            if (!tok.isEmpty()) out.add(normVal(tok.replaceAll("^\"|\"$", "")));
        }
    }
    if (out.isEmpty()) {
        Matcher single = Pattern.compile("(?:equals|==|!=|<>)\\s*([A-Za-z0-9._-]+)", Pattern.CASE_INSENSITIVE).matcher(t);
        if (single.find()) out.add(normVal(single.group(1)));
        else if (fallbackSingle != null && !fallbackSingle.isBlank()) out.add(normVal(fallbackSingle));
    }
    return new AllowedSpec(neg, out);
}

private static boolean accepts(AllowedSpec spec, String valueN) {
    String v = normVal(valueN);
    return spec.isNegated ? !spec.items.contains(v) : spec.items.contains(v);
}

// ---- NONE-row v2 chooser that honours IN vs NOT IN semantics ----
private static String pickNegV2WithSpec(String rightClause, String goodV2) {
    AllowedSpec spec = parseAllowedSpec(rightClause, goodV2);
    if (!spec.items.isEmpty()) {
        if (spec.isNegated) {
            // To FAIL a NOT-IN: pick a token inside the forbidden set
            return spec.items.iterator().next();
        } else {
            // To FAIL an IN: pick a token outside the allowed set
            for (String cand : new String[]{"ZZZ","__NEG__","99X"}) {
                if (!spec.items.contains(normVal(cand))) return cand;
            }
        }
    }
    return (goodV2 == null || goodV2.isBlank()) ? "__NEG__" : goodV2 + "_X";
}

// ---- CSV writing helpers ----
private static void writeCsv(Path file, OpenOption[] opts, String c1, String c2, String c3, String c4) {
    String row = csv(c1)+","+csv(c2)+","+csv(c3)+","+csv(c4)+"\n";
    try { Files.writeString(file, row, StandardCharsets.UTF_8, opts); }
    catch (Exception e) { throw new RuntimeException("CSV write failed: "+file, e); }
}
private static String csv(String s) {
    if (s == null) s = "";
    boolean q = s.contains(",") || s.contains("\"") || s.contains("\n");
    String t = s.replace("\"","\"\"");
    return q ? "\"" + t + "\"" : t;
}

// Keep id+err aligned when moving the "current" rule first
private static void moveToFront(List<String> ids, List<String> errs, String currentId) {
    if (currentId == null) return;
    int i = ids.indexOf(currentId);
    if (i > 0) { ids.add(0, ids.remove(i)); errs.add(0, errs.remove(i)); }
}

// ===============================================
// === [B] DECLARE THESE FIELDS BEFORE THE LOOP ===
// ===============================================
// Put these right before your collectAll row-processing loop begins (same scope as your existing maps).

Map<KeyV1, Impact>   indexByV1   = new HashMap<>(); // all combos: (combo, v1) -> cohort
Map<KeyPair, Impact> indexPair   = new HashMap<>(); // SP-SP positives: (combo, canonicalPair) -> cohort
Map<KeyV1, List<Candidate>> candidatesByLeft = new HashMap<>(); // SP-SP negatives: left value buckets
List<Buf> buffer = new ArrayList<>(); // rows to emit in original order

// ===============================================
// === [C] INSIDE YOUR COLLECT LOOP (PER RULE)  ===
// ===============================================
// Insert this inside your existing loop where you already have row.id(), row.errorCode(),
// row.ifCondition() (left), row.thenCondition() (right), and v1/v2 values computed.
// IMPORTANT: v2 here should be the *single display token* (if the hit gave a list, pick one earlier).

{
// 1) Normalise once and reuse these exact strings everywhere
String comboN = normCombo(row.comboKey());    // e.g., "SP-SP", "SP-AI"
String v1N    = normVal(row.firstValue());    // LEFT value (single)
String v2N    = normVal(row.secondValue());   // RIGHT display value (single token)

// 2) Index A: all combos group by (combo, v1)
    indexByV1.computeIfAbsent(new KeyV1(comboN, v1N), k -> new Impact())
        .add(row.id(), row.errorCode());

        // 3) SP-SP: build cohorts using ALL possible right-side values (positive lists) or candidates (negative)
        if ("SP-SP".equals(comboN)) {
AllowedSpec rightSpec = parseAllowedSpec(row.thenCondition(), v2N);

        if (!rightSpec.isNegated) {
        // Positive list: expand all pairs canonical(v1, r)
        for (String r : rightSpec.items) {
String pair = canonicalPair(v1N, r);
                if (pair != null) {
        indexPair.computeIfAbsent(new KeyPair(comboN, pair), k -> new Impact())
        .add(row.id(), row.errorCode());
        }
        }
        } else {
        // Negative list: stash candidate by left value; we'll test 'accepts' at emit
        candidatesByLeft.computeIfAbsent(new KeyV1(comboN, v1N), k -> new ArrayList<>())
        .add(new Candidate(row.id(), row.errorCode(), rightSpec));
        }

        // Buffer with canonical pair key (for single lookup later)
        buffer.add(new Buf(comboN, row.id(), row.errorCode(), v1N, v2N, canonicalPair(v1N, v2N), row.thenCondition()));

        } else {
        // Non-symmetric combos: just buffer (pairKey null)
        buffer.add(new Buf(comboN, row.id(), row.errorCode(), v1N, v2N, null, row.thenCondition()));
        }
        }

// ===============================================
// === [D] AFTER THE LOOP: EMIT THE CSV LINES   ===
// ===============================================
// Paste this right after the collect loop finishes.

Path out = Path.of("output/rules.csv");
Files.createDirectories(out.getParent());
OpenOption[] FIRST  = new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
OpenOption[] APPEND = new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND};

boolean firstWrite = true;

for (Buf b : buffer) {
// 1) Build the cohort for this row
Impact cohort;
    if ("SP-SP".equals(b.comboN())) {
// Start with positives from the pair index (if any)
Impact pos = (b.pairKey() == null) ? null : indexPair.get(new KeyPair(b.comboN(), b.pairKey()));
Impact merged = new Impact();
        if (pos != null) pos.idToErr.forEach(merged::add);

// Add negative-list candidates: (left=v1 accepts v2) and (left=v2 accepts v1)
        for (Candidate c : candidatesByLeft.getOrDefault(new KeyV1(b.comboN(), b.v1N()), List.of()))
        if (accepts(c.spec(), b.v2N())) merged.add(c.ruleId(), c.err());
        for (Candidate c : candidatesByLeft.getOrDefault(new KeyV1(b.comboN(), b.v2N()), List.of()))
        if (accepts(c.spec(), b.v1N())) merged.add(c.ruleId(), c.err());

cohort = merged;
    } else {
cohort = indexByV1.getOrDefault(new KeyV1(b.comboN(), b.v1N()), new Impact());
        }

// 2) Materialise aligned lists (ids/errors) and ensure current rule leads
ArrayList<String> ids  = new ArrayList<>(cohort.idToErr.keySet());
ArrayList<String> errs = new ArrayList<>(ids.size());
    for (String id : ids) errs.add(cohort.idToErr.get(id));

moveToFront(ids, errs, b.ruleId()); // presentation: current rule first on its own row

String rulesJoined  = String.join("|", ids.isEmpty()  ? List.of(b.ruleId()) : ids);
String errorsJoined = String.join("|", errs.isEmpty() ? List.of(b.err())    : errs);

// 3) Write the trigger row
writeCsv(out, firstWrite ? FIRST : APPEND, rulesJoined, errorsJoined, b.v1N(), b.v2N());
firstWrite = false;

// 4) Write the NONE row (same errors, v1; pick a v2 that fails condition #2)
String negV2 = pickNegV2WithSpec(b.rightClause(), b.v2N());
writeCsv(out, APPEND, "NONE", errorsJoined, b.v1N(), negV2);
        }




// Parse right clause to: negation flag + the set of tokens.
// Works for: equals / in / one of / NOT variants, with unquoted CSV/pipe/space.
// Falls back to the provided fallbackSingle if parsing fails.
static final class AllowedSpec {
    final boolean isNegated;
    final java.util.LinkedHashSet<String> items;
    AllowedSpec(boolean n, java.util.Collection<String> i) {
        isNegated = n; items = new java.util.LinkedHashSet<>(i);
    }
}
private static String normVal(String s){ return s==null? "" : s.trim().toUpperCase(); }

private static AllowedSpec parseAllowedSpec(String clauseText, String fallbackSingle) {
    String t = clauseText == null ? "" : clauseText.trim();
    boolean neg = java.util.regex.Pattern
            .compile("\\b(is\\s+not\\s+one\\s+of|not\\s+one\\s+of|not\\s+in|!=|<>)\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(t).find();

    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();

    java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:is\\s+not\\s+one\\s+of|not\\s+one\\s+of|not\\s+in|one\\s+of|in|equals|==|!=|<>)\\s+(.+)$",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(t);
    if (m.find()) {
        String rhs = m.group(1).trim().replaceAll("^\\[|\\]$", "");
        for (String tok : rhs.split("[,|\\s]+")) {
            tok = tok.trim();
            if (!tok.isEmpty()) out.add(normVal(tok.replaceAll("^\"|\"$", "")));
        }
    }

    if (out.isEmpty()) {
        java.util.regex.Matcher single = java.util.regex.Pattern
                .compile("(?:equals|==|!=|<>)\\s*([A-Za-z0-9._-]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(t);
        if (single.find()) out.add(normVal(single.group(1)));
        else if (fallbackSingle != null && !fallbackSingle.isBlank()) out.add(normVal(fallbackSingle));
    }
    return new AllowedSpec(neg, out);
}

private static boolean accepts(AllowedSpec spec, String value) {
    String v = normVal(value);
    return spec.isNegated ? !spec.items.contains(v) : spec.items.contains(v);
}

// 🔴 The key fix: always return a v2 that FLIPS the truth value.
private static String pickNegV2WithSpec(String rightClause, String goodV2) {
    AllowedSpec spec = parseAllowedSpec(rightClause, goodV2);

    // If the "good" value already (incorrectly) fails, keep it but mark — very rare.
    if (!accepts(spec, goodV2)) return goodV2;

    // POSITIVE predicates (IN / ONE OF / EQUALS): choose a token NOT in the allowed set.
    if (!spec.isNegated) {
        // deterministic probes that are unlikely to appear in real sets
        for (String cand : new String[]{"ZZZ","__NEG__","NOPE","99X"}) {
            if (!spec.items.contains(normVal(cand))) return cand;
        }
        // fallback: mutate the good value until it’s outside the set
        String base = normVal(goodV2);
        for (int i = 1; i <= 5; i++) {
            String cand = base + "_X" + i;
            if (!spec.items.contains(cand)) return cand;
        }
        return base + "_NEG"; // last resort
    }

    // NEGATIVE predicates (NOT IN / NOT ONE OF / !=): choose a token INSIDE the forbidden set.
    if (!spec.items.isEmpty()) return spec.items.iterator().next();

    // No items parsed? fabricate a token that will be considered "inside"
    return normVal(goodV2); // with a negated empty set, everything is accepted; this is the best we can do
}


// Trigger row
writeCsv(out, opts, rulesJoined, errorsJoined, b.v1N(), b.v2N());

// NONE row — keep v1 the same, but pick a v2 that DOES NOT satisfy the right clause
String negV2 = pickNegV2WithSpec(b.rightClause(), b.v2N());
writeCsv(out, appendOpts, "NONE", errorsJoined, b.v1N(), negV2);
