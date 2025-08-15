package uk.gov.hmrc.rules.tools;

import uk.gov.hmrc.rules.toggle.RuleToggles;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ToggleGenerator {

    /**
     * Build (or rebuild) rule-toggles.json by scanning the filesystem.
     * @param dslrRoot   root folder containing *.dslr (e.g. "src/main/resources/rules")
     * @param dslRoot    root folder containing *.dsl  (e.g. "src/main/resources/rules/dsl")
     * @param togglesOut path to write toggles (e.g. "src/main/resources/config/rule-toggles.json")
     */
    public static void build(Path dslrRoot, Path dslRoot, Path togglesOut) {
        RuleToggles existing = null;
        if (Files.exists(togglesOut)) {
            try {
                existing = RuleToggles.load(togglesOut);
            } catch (Exception ignored) {
                // If the existing file is malformed, we’ll rebuild fresh.
            }
        }

        Set<String> discoveredIds = discoverIdsFromDslr(dslrRoot);
        List<String> globalDsl = discoverGlobalDsl(dslRoot);

        RuleToggles merged = RuleToggles.merge(existing, discoveredIds, globalDsl);
        RuleToggles.save(togglesOut, merged);
    }

    /** Find IDs from files named like BR236_rules.dslr (first 5 chars become ID). */
    private static Set<String> discoverIdsFromDslr(Path dslrRoot) {
        if (!Files.exists(dslrRoot)) return Collections.emptySet();
        try (Stream<Path> stream = Files.walk(dslrRoot)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith("_rules.dslr"))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(ToggleGenerator::extractIdFromDslrFilename)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new)); // preserve discovery order
        } catch (IOException e) {
            throw new IllegalStateException("Failed scanning DSLR root: " + dslrRoot, e);
        }
    }

    /** Collect all .dsl paths relative to classpath root (resources). */
    private static List<String> discoverGlobalDsl(Path dslRoot) {
        if (!Files.exists(dslRoot)) return List.of();
        try (Stream<Path> stream = Files.walk(dslRoot)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".dsl"))
                    .map(ToggleGenerator::toResourcesRelative)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed scanning DSL root: " + dslRoot, e);
        }
    }

    /** Expect filenames like "BR236_rules.dslr". ID is first 5 chars. */
    private static String extractIdFromDslrFilename(String filename) {
        if (filename == null || filename.length() < 5) return null;
        // Optional extra guard: ensure it contains "_rules.dslr"
        String lower = filename.toLowerCase();
        if (!lower.endsWith("_rules.dslr")) return null;
        return filename.substring(0, 5).toUpperCase();
    }

    /** Turn an absolute path under resources into classpath-style "rules/dsl/...". */
    private static String toResourcesRelative(Path absolute) {
        // Find the "resources" segment and relativize after it
        Path p = absolute.toAbsolutePath();
        int idx = -1;
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals("resources")) { idx = i; break; }
        }
        if (idx >= 0) {
            Path rel = p.subpath(idx + 1, p.getNameCount());
            return rel.toString().replace('\\', '/');
        }
        // Fallback: return filename only
        return absolute.getFileName().toString().replace('\\', '/');
    }
}
