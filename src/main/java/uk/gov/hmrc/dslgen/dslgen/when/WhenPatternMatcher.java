package uk.gov.hmrc.dslgen.dslgen.when;

import uk.gov.hmrc.dslgen.RuleRow;
import uk.gov.hmrc.dslgen.support.DslTemplateService;
import uk.gov.hmrc.dslgen.support.DslTemplateService.TemplateMatch;
import uk.gov.hmrc.dslgen.support.PrePostConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class WhenPatternMatcher {
    private final DslTemplateService templates = new DslTemplateService();
    private final PrePostConfig prepost = new PrePostConfig();

    public List<String> collectAll(RuleRow row) {
        List<TemplateMatch> matches = new ArrayList<>();
        List<String> rawUnmatched = new ArrayList<>();

        for (String candidate : row.whenCandidates()) {
            templates.matchWhen(candidate).ifPresentOrElse(
                matches::add,
                () -> rawUnmatched.add(candidate)
            );
        }

        autoInjectMissingPrereqs(matches);
        List<String> orderedDsl = orderByRequires(matches);

        for (String raw : rawUnmatched) writeCatchall("[when] " + raw + " =");

        List<String> out = new ArrayList<>();
        out.addAll(prepost.whenPrepend());
        out.addAll(orderedDsl);
        out.addAll(prepost.whenAppend());
        return out;
    }

    private void autoInjectMissingPrereqs(List<TemplateMatch> matches) {
        Set<String> have = matches.stream().map(TemplateMatch::id).collect(Collectors.toCollection(LinkedHashSet::new));
        Deque<String> need = new ArrayDeque<>();
        for (TemplateMatch m : matches) for (String req : m.requires()) if (!have.contains(req)) need.add(req);

        while (!need.isEmpty()) {
            String reqId = need.pollFirst();
            if (have.contains(reqId)) continue;
            var defOpt = templates.findWhenById(reqId);
            if (defOpt.isEmpty()) continue;
            var def = defOpt.get();
            matches.add(new TemplateMatch(def.getId(), def.getDsl(), def.getRequires()==null?List.of():def.getRequires()));
            have.add(def.getId());
            if (def.getRequires()!=null) for (String up : def.getRequires()) if (!have.contains(up)) need.addLast(up);
        }
    }

    private List<String> orderByRequires(List<TemplateMatch> matches) {
        List<String> out = new ArrayList<>();
        Set<String> added = new HashSet<>();
        boolean progress; int guard=0;
        do {
            progress = false;
            for (TemplateMatch m : matches) {
                if (added.contains(m.id())) continue;
                if (added.containsAll(m.requires())) {
                    out.add(m.dsl());
                    added.add(m.id());
                    progress = true;
                }
            }
            if (++guard > 1000) break;
        } while (progress);

        for (TemplateMatch m : matches) if (!added.contains(m.id())) { out.add(m.dsl()); added.add(m.id()); }
        return out;
    }

    private void writeCatchall(String dslLine) {
        try {
            Path path = Path.of("target/catchall.dsl");
            if (path.getParent()!=null) Files.createDirectories(path.getParent());
            Files.writeString(path, dslLine + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catchall.dsl", e);
        }
    }
}
