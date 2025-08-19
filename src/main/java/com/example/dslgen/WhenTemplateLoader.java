package com.example.dslgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WhenTemplateLoader {

    public static WhenTemplate load(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return new WhenTemplate(List.of(), List.of(), List.of());
            }

            JsonNode root = new ObjectMapper().readTree(Files.readAllBytes(path));

            // dsl entries
            List<WhenTemplate.DslEntry> dsl = new ArrayList<>();
            if (root.has("dsl") && root.get("dsl").isArray()) {
                for (JsonNode n : root.get("dsl")) {
                    String lhs = n.has("lhs") ? n.get("lhs").asText() : "";
                    String rhs = n.has("rhs") ? n.get("rhs").asText() : "";
                    if (!lhs.isBlank() && !rhs.isBlank()) {
                        dsl.add(new WhenTemplate.DslEntry(lhs, rhs));
                    }
                }
            }

            // dslrPrepend
            List<String> prepend = new ArrayList<>();
            if (root.has("dslrPrepend") && root.get("dslrPrepend").isArray()) {
                for (JsonNode n : root.get("dslrPrepend")) {
                    prepend.add(n.asText());
                }
            }

            // dslrAppend
            List<String> append = new ArrayList<>();
            if (root.has("dslrAppend") && root.get("dslrAppend").isArray()) {
                for (JsonNode n : root.get("dslrAppend")) {
                    append.add(n.asText());
                }
            }

            return new WhenTemplate(dsl, prepend, append);
        } catch (Exception e) {
            // fail-safe: return empty template so generation still proceeds
            return new WhenTemplate(List.of(), List.of(), List.of());
        }
    }
}