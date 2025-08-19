package com.example.dslgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads config/then-template.json → ThenTemplate. */
public final class ThenTemplateLoader {
    public static ThenTemplate load(Path path) {
        try {
            if (path == null || !Files.exists(path)) return new ThenTemplate(List.of());
            JsonNode root = new ObjectMapper().readTree(Files.readAllBytes(path));
            List<String> lines = new ArrayList<>();
            if (root.has("then") && root.get("then").isArray()) {
                root.get("then").forEach(n -> lines.add(n.asText()));
            }
            return new ThenTemplate(lines);
        } catch (Exception e) {
            // Fail-safe: empty template rather than aborting generation
            return new ThenTemplate(List.of());
        }
    }
}
