package com.example.ruleeditor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    @Autowired
    private RuleService ruleService;

    @GetMapping
    public List<Rule> getRules() throws Exception {
        return ruleService.getAllRules();
    }

    @PostMapping
    public void addRule(@RequestBody Rule rule) throws Exception {
        ruleService.addRule(rule);
    }

    @PutMapping("/{id}")
    public void updateRule(@PathVariable String id, @RequestBody Rule rule) throws Exception {
        ruleService.updateRule(id, rule);
    }
}