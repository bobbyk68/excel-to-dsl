package com.example.ruleeditor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class RuleService {
    private final File file = new File("src/main/resources/rules.json");
    private final ObjectMapper mapper = new ObjectMapper();

    public synchronized List<Rule> getAllRules() throws Exception {
        if (!file.exists()) return new ArrayList<>();
        return mapper.readValue(file, new TypeReference<>() {});
    }

    public synchronized void addRule(Rule rule) throws Exception {
        List<Rule> rules = getAllRules();
        rules.add(rule);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, rules);
    }

    public synchronized void updateRule(String id, Rule newRule) throws Exception {
        List<Rule> rules = getAllRules();
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).getId().equals(id)) {
                newRule.setId(id);
                rules.set(i, newRule);
                break;
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, rules);
    }
}