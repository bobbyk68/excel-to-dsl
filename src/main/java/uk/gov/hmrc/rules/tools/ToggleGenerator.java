package uk.gov.hmrc.rules.tools;

import uk.gov.hmrc.rules.toggle.RuleToggles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ToggleGenerator {
    private static final Pattern RULE_HDR = Pattern.compile("(?m)^\\s*rule\\s*\"([^\"]+)\"\\s*$");

    public static void build(Path dslrRoot, Path dslRoot, Path togglesOut) {
        RuleToggles existing = null;
        if (Files.exists(togglesOut)) {
            try { existing = RuleToggles.load(togglesOut); } catch (Exception ignored) {}
        }

        var discovered = discoverRules(dslrRoot, dslRoot);
        var globalDsl  = discoverGlobalDsl(dslRoot);

        RuleToggles merged = RuleToggles.merge(existing, discovered, globalDsl);
        RuleToggles.save(togglesOut, merged);
    }

    /** Scan each *_rules.dslr, extract rule "..." names, map to (file,dsl). */
    private static List<RuleToggles.RuleToggle> discoverRules(Path dslrRoot, Path dslRoot) {
        if (!Files.exists(dslrRoot)) return List.of();
        List<RuleToggles.RuleToggle> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dslrRoot)) {
            for (Path p : s.filter(pp -> pp.toString().toLowerCase().endsWith("_rules.dslr")).collect(Collectors.toList())) {
                String relDslr = toResourcesRelative(p);
                String base    = p.getFileName().toString().replaceAll("\\.dslr$", "");
                // assume matching DSL alongside by convention; ok if missing (will fail at build time)
                String dslPath = toResourcesRelative(dslRoot.resolve(base + ".dsl"));
                String text    = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = RULE_HDR.matcher(text);
                while (m.find()) {
                    String ruleName = m.group(1).trim();
                    out.add(new RuleToggles.RuleToggle(ruleName, relDslr, dslPath, false));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed scanning DSLR root: " + dslrRoot, e);
        }
        return out;
    }

    private static List<String> discoverGlobalDsl(Path dslRoot) {
        if (!Files.exists(dslRoot)) return List.of();
        try (Stream<Path> stream = Files.walk(dslRoot)) {
            return stream.filter(p -> p.toString().toLowerCase().endsWith(".dsl"))
                    .map(ToggleGenerator::toResourcesRelative)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed scanning DSL root: " + dslRoot, e);
        }
    }

    private static String toResourcesRelative(Path absolute) {
        Path p = absolute.toAbsolutePath();
        int idx = -1;
        for (int i = 0; i < p.getNameCount(); i++) {
            if ("resources".equals(p.getName(i).toString())) { idx = i; break; }
        }
        if (idx >= 0) {
            Path rel = p.subpath(idx + 1, p.getNameCount());
            return rel.toString().replace('\\', '/');
        }
        return absolute.getFileName().toString().replace('\\', '/');
    }
}
