package uk.gov.hmrc.rules.engine;

import org.drools.io.ResourceType;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSessionPool;
import uk.gov.hmrc.rules.toggle.RuleToggles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;
import java.util.stream.Collectors;

public class DroolsRuleEngine {
    private final KieServices ks = KieServices.Factory.get();
    private final AtomicReference<KieContainer> containerRef = new AtomicReference<>();
    private final AtomicReference<KieSessionPool> poolRef = new AtomicReference<>();

    private static final Pattern RULE_HDR   = Pattern.compile("(?m)^\\s*rule\\s*\"([^\"]+)\"\\s*$");
    private static final Pattern RULE_BLOCK = Pattern.compile("(?ms)^\\s*rule\\s*\"([^\"]+)\"\\s*\\R(.*?)\\bend\\b\\s*");

    private final String mainDslPath;   // e.g. rules/dsl/main.dsl
    private final String mainDslrPath;  // e.g. rules/main.dslr

    public DroolsRuleEngine(String mainDslPath, String mainDslrPath) {
        this.mainDslPath = mainDslPath;
        this.mainDslrPath = mainDslrPath;
    }

    public synchronized void buildFromToggles(Path togglesJson, boolean fsRules) {
        RuleToggles toggles = RuleToggles.load(togglesJson);
        var kfs = ks.newKieFileSystem();

        // (A) ALWAYS-ON HEADERS
        writeResource(kfs, mainDslPath, fsRules, ResourceType.DSL);    // load DSL header first
        writeResource(kfs, mainDslrPath, fsRules, ResourceType.DESCR); // then DSLR header

        // (B) GLOBAL DSLs (e.g., BRxxx_rules.dsl you also want available)
        if (toggles.globalDsl != null) {
            // Deduplicate in case main.dsl is also listed globally
            var seen = new LinkedHashSet<String>();
            for (String dsl : toggles.globalDsl) {
                if (dsl.equals(mainDslPath)) continue;
                if (seen.add(dsl)) writeResource(kfs, dsl, fsRules, ResourceType.DSL);
            }
        }

        // (C) GROUP ENABLED RULES BY DSLR FILE AND FILTER CONTENT
        Map<String, Set<String>> enabledByFile = toggles.rules.stream()
                .filter(r -> r.enabled)
                .collect(Collectors.groupingBy(r -> r.file, Collectors.mapping(r -> r.id, Collectors.toSet())));

        for (var e : enabledByFile.entrySet()) {
            String dslrPath = e.getKey();
            // Skip the header file if someone listed its rules in toggles (header is always compiled whole)
            if (dslrPath.equals(mainDslrPath)) continue;

            Set<String> keep = e.getValue();
            String original = readResourceText(dslrPath, fsRules);
            String preamble = extractPreamble(original);
            String filtered = preamble + extractEnabledRuleBlocks(original, keep);

            if (!filtered.isBlank()) {
                // write synthetic filtered DSLR (so only enabled blocks compile)
                String syntheticPath = "_generated/" + dslrPath.replace('/', '_');
                kfs.write("src/main/resources/" + syntheticPath, filtered.getBytes(StandardCharsets.UTF_8));
            }
        }

        // (D) BUILD & SWAP
        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("Build errors: " + results.getMessages());
        }

        var rid = kb.getKieModule().getReleaseId();
        var newC = ks.newKieContainer(rid);
        var newP = newC.newKieSessionPool(16);

        var oldC = containerRef.getAndSet(newC);
        var oldP = poolRef.getAndSet(newP);
        if (oldC != null) oldC.dispose();
    }

    private static String extractPreamble(String text) {
        var m = RULE_HDR.matcher(text);
        return m.find() ? text.substring(0, m.start()) : text;
    }
    private static String extractEnabledRuleBlocks(String text, Set<String> enabledIds) {
        StringBuilder sb = new StringBuilder();
        var m = RULE_BLOCK.matcher(text);
        while (m.find()) {
            String name = m.group(1).trim();
            if (enabledIds.contains(name)) {
                sb.append(m.group(0).trim()).append(System.lineSeparator()).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    private void writeResource(KieFileSystem kfs, String relPath, boolean fs, org.drools.io.ResourceType type) {
        var res = fs
                ? ks.getResources().newFileSystemResource(Path.of(relPath).toFile())
                : ks.getResources().newClassPathResource(relPath);
        if (res == null) throw new IllegalArgumentException("Resource not found: " + relPath);
        res.setResourceType(type);
        kfs.write("src/main/resources/" + relPath, res);
    }

    private String readResourceText(String relPath, boolean fs) {
        try {
            if (fs) return java.nio.file.Files.readString(Path.of(relPath));
            try (var is = Objects.requireNonNull(
                    getClass().getClassLoader().getResourceAsStream(relPath),
                    "Classpath resource not found: " + relPath)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read: " + relPath, e);
        }
    }

    public KieContainer container()     { return containerRef.get(); }
    public KieSessionPool sessionPool() { return poolRef.get(); }
}
