package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SignContractServiceTest {

    private final LeasingProcess process = mock(LeasingProcess.class);
    private final SignContractService underTest = new SignContractService(process);

    @Test
    @DisplayName("signContract correlates the contract-signed message")
    void signContractCorrelatesTheContractSignedMessage() {

        // given: an application id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        // when: the contract is signed
        underTest.signContract(id);

        // then: the contract-signed message is correlated for that application
        then(process).should().correlateContractSigned(id);
        then(process).shouldHaveNoMoreInteractions();
    }
}
