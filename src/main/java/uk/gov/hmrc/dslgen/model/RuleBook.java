package uk.gov.hmrc.dslgen.model;

import java.util.List;

public class RuleBook {

    private List<AtomicText> atomics;
    private List<List<String>> composites;
    private Stats stats;

    public RuleBook() {}

    public RuleBook(List<AtomicText> atomics, List<List<String>> composites, Stats stats) {
        this.atomics = atomics;
        this.composites = composites;
        this.stats = stats;
    }

    public List<AtomicText> getAtomics() { return atomics; }
    public void setAtomics(List<AtomicText> atomics) { this.atomics = atomics; }

    public List<List<String>> getComposites() { return composites; }
    public void setComposites(List<List<String>> composites) { this.composites = composites; }

    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }

    public static record Stats(int uniqueCount, List<Integer> duplicateRowIds) {}
}
