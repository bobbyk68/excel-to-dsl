package uk.gov.hmrc.dslgen;

public class Pipeline {

    public java.util.List<RuleRow> run(String excelPath) {
        java.util.List<RuleRow> rows = excelReader.read(excelPath);

        DuplicateIfPreMerger.Result res = DuplicateIfPreMerger.mergeByRawIf(rows);

        // Optional: log removed rows for audit
        // for (RuleRow r : res.removed()) { log.warn("Removed dup IF: {}", r.ifCondition()); }

        // Only unique rows proceed; THEN side available via mergedThenCodesCsv()
        return whenPatternMatcher.collectAll(res.kept());
    }

    private final ExcelReader excelReader;
    private final WhenPatternMatcher whenPatternMatcher;

    public Pipeline(ExcelReader excelReader, WhenPatternMatcher whenPatternMatcher) {
        this.excelReader = excelReader;
        this.whenPatternMatcher = whenPatternMatcher;
    }
}
