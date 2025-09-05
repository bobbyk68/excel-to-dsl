package uk.gov.hmrc.dslgen.service;

import uk.gov.hmrc.dslgen.io.ExcelReader;
import uk.gov.hmrc.dslgen.model.AtomicText;
import uk.gov.hmrc.dslgen.model.RuleBook;
import uk.gov.hmrc.dslgen.util.TextCanonicalizer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deduplicates IF/THEN rows into a single atomics list and composite pairs.
 */
public final class DedupService {

    private DedupService() {}

    public static RuleBook buildRuleBook(List<ExcelReader.IfThenRow> rows) {
        // dictionary of text -> id
        Map<String, String> textToId = new LinkedHashMap<>();
        List<AtomicText> atomicsOut = new ArrayList<>();
        List<List<String>> compositesOut = new ArrayList<>();

        // to detect duplicates
        Set<String> seenPairs = new HashSet<>();
        List<Integer> duplicateRowIds = new ArrayList<>();

        AtomicInteger seq = new AtomicInteger(1);

        for (ExcelReader.IfThenRow r : rows) {
            String left  = TextCanonicalizer.canonical(r.ifBlock());
            String right = TextCanonicalizer.canonical(r.thenBlock());

            String id1 = textToId.computeIfAbsent(left,  k -> nextId(seq, atomicsOut, k));
            String id2 = textToId.computeIfAbsent(right, k -> nextId(seq, atomicsOut, k));

            String pairKey = id1 + "|" + id2;
            if (!seenPairs.add(pairKey)) {
                duplicateRowIds.add(r.rowNumber());
                continue;
            }

            compositesOut.add(List.of(id1, id2));
        }

        RuleBook.Stats stats = new RuleBook.Stats(atomicsOut.size(), duplicateRowIds);
        return new RuleBook(atomicsOut, compositesOut, stats);
    }

    private static String nextId(AtomicInteger seq, List<AtomicText> atomics, String text) {
        int n = seq.getAndIncrement();
        String id = "a" + (n < 100 ? String.format("%02d", n) : n);
        atomics.add(new AtomicText(id, text));
        return id;
    }
}
  f