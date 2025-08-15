package uk.gov.hmrc.rules.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmrc.rules.tools.ToggleGenerator;

import java.nio.file.Path;

@RestController
@RequestMapping("/toggles")
public class ToggleAdminController {

    @Value("${rules.dslr.root:src/main/resources/rules}")
    private String dslrRoot;

    @Value("${rules.dsl.root:src/main/resources/rules/dsl}")
    private String dslRoot;

    @Value("${rules.toggles.path:src/main/resources/config/rule-toggles.json}")
    private String togglesOut;

    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild() {
        ToggleGenerator.build(Path.of(dslrRoot), Path.of(dslRoot), Path.of(togglesOut));
        return ResponseEntity.ok("rule-toggles.json rebuilt from .dslr and .dsl");
    }
}
