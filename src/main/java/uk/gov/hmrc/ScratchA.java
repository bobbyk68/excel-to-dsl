// normalise keys so matches are stable
private static String normCombo(String s){ return s == null ? "NA-NA" : s.trim().toUpperCase(); }
private static String normVal(String s){ return s == null ? "" : s.trim().toUpperCase(); }

// SP-SP means symmetric: left/right are the same domain
private static boolean isSymmetricCombo(String comboN){
    String[] p = normCombo(comboN).split("\\s*-\\s*");
    return p.length == 2 && p[0].equals(p[1]);  // e.g., SP-SP, RP-RP, AD-AD
}

// canonical, order-independent pair key for SP-SP: e.g., D03|D19 == D19|D03
private static String canonicalPair(String v1N, String v2N){
    if (v1N.isBlank() || v2N.isBlank()) return null;
    return (v1N.compareTo(v2N) <= 0) ? (v1N + "|" + v2N) : (v2N + "|" + v1N);
}


// impact set: we join ruleIds & errorCodes with pipes later
record Impact(java.util.Set<String> ruleIds, java.util.Set<String> errorCodes) {
    static Impact create() {
        return new Impact(new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>());
    }
}

// keys for the two indexes
record KeyV1(String combo, String v1) {}
record KeyPair(String combo, String pairKey) {}

// the two indexes
Map<KeyV1, Impact> indexByV1   = new java.util.HashMap<>(); // all combos
Map<KeyPair, Impact> indexPair = new java.util.HashMap<>(); // SP-SP only

// buffer for emit (store normalised values)
record Buf(String comboN, String ruleId, String err, String v1N, String v2N, String pairKey, String rightClause) {}
java.util.List<Buf> buffer = new java.util.ArrayList<>();


// 0) You already computed these for the row:
String combo = row.comboKey();              // Option B (from clause text)
String v1    = row.firstValue();            // from left AtomicHit.rawValue()
String v2Raw = row.secondValue();           // after your "pick one" selection

// 1) Normalise once and reuse these exact strings everywhere
String comboN = normCombo(combo);           // e.g., "SP-SP"
String v1N    = normVal(v1);                // e.g., "D19"
String v2N    = normVal(v2Raw);             // e.g., "D03"

// 2) Register into Index A: (combo, v1) for ALL combos
Impact impA = indexByV1.computeIfAbsent(new KeyV1(comboN, v1N), k -> Impact.create());
impA.ruleIds.add(row.id());
        impA.errorCodes.add(row.errorCode());

// 3) If symmetric (SP-SP etc.), also register into Index B by canonical unordered pair
String pairKey = null;
if (isSymmetricCombo(comboN)) {
pairKey = canonicalPair(v1N, v2N);     // becomes something like "D03|D19"
    if (pairKey != null) {
Impact impB = indexPair.computeIfAbsent(new KeyPair(comboN, pairKey), k -> Impact.create());
        impB.ruleIds.add(row.id());
        impB.errorCodes.add(row.errorCode());
        }
        }

// 4) Buffer (store the normalised values and pairKey you just used)
        buffer.add(new Buf(comboN, row.id(), row.errorCode(), v1N, v2N, pairKey, row.thenCondition()));





java.nio.file.Path out = java.nio.file.Path.of("output/rules.csv");
// open options as you already have…

for (Buf b : buffer) {
Impact imp = ("SP-SP".equals(b.comboN()))
        ? indexPair.getOrDefault(new KeyPair(b.comboN(), b.pairKey()), Impact.create())
        : indexByV1.getOrDefault(new KeyV1(b.comboN(), b.v1N()),      Impact.create());

String rulesJoined  = String.join("|", imp.ruleIds.isEmpty()  ? java.util.List.of(b.ruleId()) : imp.ruleIds);
String errorsJoined = String.join("|", imp.errorCodes.isEmpty()? java.util.List.of(b.err())    : imp.errorCodes);

// 1) trigger row
writeCsv(out, optsFirstOrAppend, rulesJoined, errorsJoined, b.v1N(), b.v2N());

// 2) NONE row (keep v1, break only condition #2)
String negV2 = pickNegativeSecondValue(b.rightClause(), b.v2N());
writeCsv(out, optsAppend, "NONE", errorsJoined, b.v1N(), negV2);
        }
