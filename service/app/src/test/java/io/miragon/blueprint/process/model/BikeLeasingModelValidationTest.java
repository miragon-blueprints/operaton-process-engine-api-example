package io.miragon.blueprint.process.model;

import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.SingleModelValidationRule;
import io.miragon.bpmn.testing.BpmnRules;
import io.miragon.bpmn.testing.BpmnValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the BPMN models themselves (structure, not behaviour) with the {@code bpmn-to-code-testing}
 * rule engine: all built-in rules ({@link BpmnRules#all()}) plus the custom {@link ServiceTaskExternalTopicRule}.
 * Runs at build time from the classpath — no engine required.
 */
class BikeLeasingModelValidationTest {

    @Test
    @DisplayName("the bpmn models satisfy all rules and only use external task topics")
    void theBpmnModelsSatisfyAllRulesAndOnlyUseExternalTaskTopics() {
        List<SingleModelValidationRule> rules = new ArrayList<>(BpmnRules.all());
        rules.add(new ServiceTaskExternalTopicRule());

        BpmnValidator
            .fromClasspath("bpmn/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(rules)
            .validate()
            .assertNoViolations();
    }
}
