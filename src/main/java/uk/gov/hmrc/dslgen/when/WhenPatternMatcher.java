package uk.gov.hmrc.dslgen.when;

import uk.gov.hmrc.dslgen.RuleRow;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class WhenPatternMatcher {

    private final List<String> prepend = List.of("$doc : Document()");
    private final List<String> append = List.of();

    public List<String> collectAll(RuleRow row) {
        List<String> results = new ArrayList<>();
        results.addAll(prepend);

        for (String candidate : row.whenCandidates()) {
            results.add(tryMatch(candidate));
        }

        results.addAll(append);
        return results;
    }

    private String tryMatch(String phrase) {
        if (phrase.startsWith("Declaration type oneof")) {
            String values = phrase.replace("Declaration type oneof", "").trim();
            return "declaration.type in (" + values + ")";
        }
        if (phrase.contains("AuthorizationHolder.authorizationType.code must equal")) {
            String val = phrase.substring(phrase.lastIndexOf("equal") + 5).trim();
            return "$doc.authCode == \"" + val + "\"";
        }

        // fallback → catchall.dsl
        writeCatchall("[when]" + phrase + " = " + phrase);
        return phrase;
    }

    private void writeCatchall(String dslLine) {
        try {
            Path path = Path.of("target/catchall.dsl");
            Files.writeString(path, dslLine + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catchall.dsl", e);
        }
    }
}