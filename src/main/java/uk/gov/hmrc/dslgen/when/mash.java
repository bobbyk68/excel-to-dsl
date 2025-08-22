package uk.gov.hmrc.dslgen.when;

public class mash {

    // --- WHEN ---
    List<String> whenPhrasesRaw = new ArrayList<>();
whenPhrasesRaw.addAll(whenTemplate.prepend);

if (whenMatcher instanceof WhenPatternMatcher w) {
        whenPhrasesRaw.addAll(w.collectAll(row));
    } else {
        whenMatcher.tryMatch(row).ifPresent(whenPhrasesRaw::add);
    }

whenPhrasesRaw.addAll(whenTemplate.append);

// map phrase -> IDs etc...





}


// --- THEN ---
List<String> thenLines = new ArrayList<>();
thenLines.addAll(thenTemplate.prepend);

if (thenMatcher instanceof ThenPatternMatcher t) {
        thenLines.addAll(t.collectAll(row));
        } else {
        thenMatcher.tryMatch(row).ifPresent(thenLines::add);
}

        thenLines.addAll(thenTemplate.append);






HybridPatternMatcher hybrid = new HybridPatternMatcher();

DslBuilder builder = new DslBuilder(
        new WhenPatternMatcher(hybrid),
        new ThenPatternMatcher(hybrid),
        Paths.get("src/main/resources/config/when-template.json"),
        Paths.get("src/main/resources/config/then-template.json")
);
