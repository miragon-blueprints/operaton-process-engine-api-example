package io.miragon.blueprint.adapter.outbound.insurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingInsuranceAdapterTest {

    private final LoggingInsuranceAdapter underTest = new LoggingInsuranceAdapter();

    @Test
    @DisplayName("issuePolicy logs without error")
    void issuePolicyLogsWithoutError() {
        // given: an application / when-then: issuing the policy runs without error
        assertThatCode(() -> underTest.issuePolicy(testLeasingApplication().build().id())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cancelPolicy logs without error")
    void cancelPolicyLogsWithoutError() {
        // given: an application / when-then: cancelling the policy runs without error
        assertThatCode(() -> underTest.cancelPolicy(testLeasingApplication().build().id())).doesNotThrowAnyException();
    }
}
