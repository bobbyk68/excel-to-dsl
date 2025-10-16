public void collectAll(List<RuleRow> rows, EmitterRegistry registry) throws IOException {
    dslr.appendPackage();
    SimpleDslFileWriter dsl = new SimpleDslFileWriter();

    for (RuleRow row : rows) {
        try {
            // ---- Step 2: Build AtomicHits from JSON ----
            // Split IF into up to two clauses (PRIMARY + MATCHING)
            String[] ifParts = splitIfClauses(row.ifCondition());

            AtomicHit ifPrimary  = (ifParts.length >= 1 && !ifParts[0].isBlank())
                    ? matcher.matchIf(ifParts[0])
                    : null;

            AtomicHit ifMatching = (ifParts.length >= 2 && !ifParts[1].isBlank())
                    ? matcher.matchIf(ifParts[1])
                    : null;

            // THEN: prefer explicit thenCondition; else derive from combined/merged fields if present
            String thenLiteral = chooseThenLiteral(row);
            AtomicHit thenHit  = (thenLiteral != null) ? matcher.matchThen(thenLiteral) : null;

            if (ifPrimary == null) {
                System.out.println("SKIP: no IF match for rule " + row.id());
                continue;
            }

            // ---- Step 3: Shape → EmitContext (promotion + absence handled here) ----
            EmitContext ctx = Shaper.shape(ifPrimary, ifMatching, thenHit);

            // ---- Step 4: Emit via registry ----
            boolean handled = registry.dispatch(ctx, dsl);
            if (!handled) {
                // fallback text (or rely on a DefaultFallbackEmitter registered last)
                dsl.whenLine("Goods item exists");
                dsl.thenLine("Emit BR validation error");
            }

            // Wrap with your rule header/footer
            dslr.appendNewRule(row);
            dslr.append(dsl.getText());
            dsl.reset();

        } catch (Exception e) {
            System.out.println("FAIL error: " + row.id() + " :: " + e.getMessage());
            // continue to next row
        }
    }
}

/* ---------------- helpers that fit your RuleRow ---------------- */

/** Split a single English IF field into up to two clauses. */
private String[] splitIfClauses(String ifCondition) {
    if (ifCondition == null) return new String[0];

    // Prefer explicit newlines from Excel; otherwise split on “ and ” between sentences
    String text = ifCondition.trim();

    // 1) If Excel has real line breaks → use them
    if (text.contains("\n")) {
        String[] parts = text.split("\\r?\\n", 2);
        return new String[] { parts[0].trim(), parts.length > 1 ? parts[1].trim() : "" };
    }

    // 2) Try a safe “ and ” split between two leading “there is …” clauses
    // e.g., "there is at least one X ... and there is no Y ..."
    var m = java.util.regex.Pattern
            .compile("^(.*?)(?:\\s+and\\s+)(there\\s+is\\s+.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);
    if (m.matches()) {
        return new String[] { m.group(1).trim(), m.group(2).trim() };
    }

    // 3) Single clause
    return new String[] { text };
}

/** Pick a THEN literal from the fields you have. */
private String chooseThenLiteral(RuleRow row) {
    // 1) If explicit THEN English present, use it
    if (row.thenCondition() != null && !row.thenCondition().isBlank()) {
        return row.thenCondition().trim();
    }

    // 2) If you’ve prebuilt a combined THEN string, use that
    if (row.getCombinedThenCondition() != null && !row.getCombinedThenCondition().isBlank()) {
        return row.getCombinedThenCondition().trim();
    }

    // 3) If you only have merged codes, synthesise a minimal obligation line.
    //    Example for AI (adjust to your domain if needed).
    if (row.mergedThenCodesCsv() != null && !row.mergedThenCodesCsv().isBlank()) {
        // Produce: 'at least one GoodsItem.additionalInformation.code must equals <csv>'
        return "at least one GoodsItem.additionalInformation.code must equals " + row.mergedThenCodesCsv().trim();
    }

    // Nothing to promote from THEN
    return null;
}

// ===== 0) small enums (package-local is fine) =====
enum HitRole  { PRIMARY, MATCHING }
enum Presence { PRESENT, ABSENT }

// ===== 1) atomic-hit role/presence helpers (work with your existing AtomicHit) =====
// These assume your AtomicHit was enriched in AtomicMatcher.matchAtomic(...) using PatternIntrospector.
// If your AtomicHit already has fields for role/presence, use those instead.
final class HitUtil {
    private HitUtil() {}

    /** Decide role: first IF = PRIMARY, second IF (if you have one) = MATCHING. */
    static HitRole roleFor(boolean isSecondIfLine) {
        return isSecondIfLine ? HitRole.MATCHING : HitRole.PRIMARY;
    }

    /** Presence: use your meta (negated / “no …” / obligation) to mark ABSENT. */
    static Presence presenceFor(AtomicHit h, boolean isThenObligation) {
        // If you set hit.negated or meta.negated() in AtomicMatcher, prefer that.
        // Fallback: treat THEN-obligation (“must equals …”) as ABSENT on the matching side.
        boolean absent =
                (has(h, "negated") && getBool(h, "negated"))
                        || (has(h, "operator") && "not exists".equalsIgnoreCase(getStr(h, "operator")))
                        || isThenObligation;
        return absent ? Presence.ABSENT : Presence.PRESENT;
    }

    // --- reflective helpers so we don't force you to change your AtomicHit API right now
    private static boolean has(Object o, String m) {
        try { o.getClass().getMethod("get" + cap(m)); return true; } catch (Exception e) { return false; }
    }
    private static boolean getBool(Object o, String m) {
        try { var md = o.getClass().getMethod("get" + cap(m)); Object v = md.invoke(o); return v instanceof Boolean && (Boolean) v; } catch (Exception e){ return false; }
    }
    private static String getStr(Object o, String m) {
        try { var md = o.getClass().getMethod("get" + cap(m)); Object v = md.invoke(o); return v == null ? "" : v.toString(); } catch (Exception e){ return ""; }
    }
    private static String cap(String s){ return s.isEmpty()? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}

// ===== 2) shape IF/THEN into EmitContext (Step 3) =====
final class Shaper {

    /** Build EmitContext from your hits. Will *promote* THEN to matching IF if there is only one IF. */
    static EmitContext shape(AtomicHit ifPrimary,
                             AtomicHit ifMatchingOrNull,
                             AtomicHit thenHitOrNull) {

        // Build left (PRIMARY) condition
        Condition left = AtomicHitAdapter.toCondition(ifPrimary);

        // Decide the right (MATCHING) condition:
        Condition right = null;

        if (ifMatchingOrNull != null) {
            // We have two IF lines: PRIMARY + MATCHING
            right = AtomicHitAdapter.toCondition(ifMatchingOrNull);
        } else if (isValueBearing(thenHitOrNull)) {
            // PROMOTION: no second IF; use THEN obligation as MATCHING
            // Ensure operator is NOT_EXISTS so emitters render "No matching … exists"
            forceNotExists(thenHitOrNull);
            right = AtomicHitAdapter.toCondition(thenHitOrNull);
        }

        // Build ordered list
        java.util.List<Condition> conds = new java.util.ArrayList<>();
        conds.add(left);
        if (right != null) conds.add(right);

        // Parent anchor (GoodsItem for your cases)
        String parent = deriveParentAnchor(conds);

        // Then target key (used for final THEN wording if you want to vary it)
        String thenKey = deriveThenTargetKey(conds);

        return new EmitContext(parent, conds, new ThenPart(thenKey));
    }

    private static boolean isValueBearing(AtomicHit h) {
        if (h == null) return false;
        String f = h.getFieldToken();
        return f != null && !f.isBlank() && !java.util.Set.of("VALIDATION","RP_PP","SP_AI","SP_AD","SP")
                .contains(f.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static void forceNotExists(AtomicHit h) {
        try {
            var m = h.getClass().getMethod("setOperatorToken", String.class);
            m.invoke(h, "not exists");
        } catch (Exception ignored) {
            // if your AtomicHit is immutable, adjust AtomicMatcher so THEN-hit already carries "not exists"
        }
    }

    private static String deriveParentAnchor(java.util.List<Condition> conds) {
        boolean allGoods = conds.stream().allMatch(c -> c.anchorKey()!=null && c.anchorKey().startsWith("GoodsItem"));
        if (allGoods) return "GoodsItem";
        String first = conds.get(0).anchorKey();
        int dot = first == null ? -1 : first.indexOf('.');
        return dot >= 0 ? first.substring(0, dot) : (first == null ? "Unknown" : first);
    }

    private static String deriveThenTargetKey(java.util.List<Condition> conds) {
        boolean hasSP = conds.stream().anyMatch(c -> "specialProcedure.code".equals(c.fieldKey()));
        boolean hasAI = conds.stream().anyMatch(c -> "additionalInformation.code".equals(c.fieldKey()));
        boolean hasAD = conds.stream().anyMatch(c -> "additionalDocuments.type.code".equals(c.fieldKey()));
        boolean hasRP = conds.stream().anyMatch(c -> "requestedProcedureCode".equals(c.fieldKey()));
        boolean hasPP = conds.stream().anyMatch(c -> "previousProcedureCode".equals(c.fieldKey()));
        if (hasSP && hasAI) return "spAndAi";
        if (hasSP && hasAD) return "spAndAd";
        if (hasRP && hasPP) return "requestedAndPrevious";
        if (hasSP)          return "specialProcedure";
        return "validation";
    }
}

// ===== 3) YOUR WhenPatternMatcher.collectAll (drop-in) =====
public void collectAll(List<RuleRow> rows, EmitterRegistry registry) throws IOException {

    dslr.appendPackage();                      // keep your existing header
    SimpleDslFileWriter dsl = new SimpleDslFileWriter();

    for (RuleRow row : rows) {
        AtomicHit ifHitPrimary = null;
        AtomicHit ifHitMatching = null;
        AtomicHit thenHit = null;

        try {
            // Step 2: JSON → AtomicHit (you already have this)
            ifHitPrimary  = matcher.matchIf(row.ifCondition());   // your first IF line
            // If your IF has a second line, call a second time with it:
            if (hasSecondIfLine(row)) {
                ifHitMatching = matcher.matchIf(row.ifCondition2()); // or however you access it
            }
            thenHit       = matcher.matchThen(row.thenCondition());

            // OPTIONAL: mark roles/presence on the hits (only if your AtomicHit doesn’t already carry it)
            // - first IF is PRIMARY; second IF (if present) is MATCHING
            stampRolePresence(ifHitPrimary,  HitRole.PRIMARY,  HitUtil.presenceFor(ifHitPrimary,  false));
            if (ifHitMatching != null) {
                stampRolePresence(ifHitMatching, HitRole.MATCHING, HitUtil.presenceFor(ifHitMatching, false));
            }
            // THEN often encodes an obligation -> treat as ABSENT on matching
            if (thenHit != null) {
                stampRolePresence(thenHit, HitRole.MATCHING, HitUtil.presenceFor(thenHit, /*isThenObligation*/ true));
            }

        } catch (Exception e) {
            System.out.println("FAIL error: " + row.id() + " :: " + e.getMessage());
            continue;
        }

        // ===== Step 3: shape → EmitContext (promotion + parent binding etc.) =====
        EmitContext ctx = Shaper.shape(ifHitPrimary, ifHitMatching, thenHit);

        // ===== Step 4: pick an emitter & render =====
        boolean handled = registry.dispatch(ctx, dsl);
        if (!handled) {
            // Fallback (or register DefaultFallbackEmitter last in the registry)
            dsl.whenLine("Goods item exists");
            dsl.thenLine("Emit BR675 validation error");
        }

        // Keep your existing composition/wrap (you already have HyphenTwoPhaseComposer above if needed)
        dslr.appendNewRule(row);
        dslr.append(dsl.getText());
        dsl.reset();
    }
}

// helper to detect second IF line — adjust to your RuleRow API
private boolean hasSecondIfLine(RuleRow row) {
    try {
        var m = row.getClass().getMethod("ifCondition2");
        Object v = m.invoke(row);
        return v != null && !v.toString().isBlank();
    } catch (Exception e) {
        return false;
    }
}

// patch role/presence into your existing AtomicHit if it supports setters; if not, ignore
private void stampRolePresence(AtomicHit hit, HitRole role, Presence presence) {
    if (hit == null) return;
    try { var m = hit.getClass().getMethod("setRole", String.class); m.invoke(hit, role.name()); } catch (Exception ignored) {}
    try { var m = hit.getClass().getMethod("setPresence", String.class); m.invoke(hit, presence.name()); } catch (Exception ignored) {}
}
