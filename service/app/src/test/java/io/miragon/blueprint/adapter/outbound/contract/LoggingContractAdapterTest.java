package io.miragon.blueprint.adapter.outbound.contract;

import io.miragon.blueprint.domain.leasing.ContractId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingContractAdapterTest {

    private final LoggingContractAdapter underTest = new LoggingContractAdapter();

    @Test
    @DisplayName("issueContract mints a fresh contract id")
    void issueContractMintsAFreshContractId() {
        // given: an application / when: a contract is issued twice
        var first = underTest.issueContract(testLeasingApplication().build().id());
        var second = underTest.issueContract(testLeasingApplication().build().id());
        // then: each issue gets its own reference
        assertThat(first.value()).startsWith("CONTRACT-");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("revokeContract logs without error")
    void revokeContractLogsWithoutError() {
        // given: an issued contract / when-then: revoking it runs without error
        assertThatCode(() -> underTest.revokeContract(new ContractId("CONTRACT-1"))).doesNotThrowAnyException();
    }
}
