package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class CancelContractServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ContractPort contract = mock(ContractPort.class);
    private final CancelContractService underTest = new CancelContractService(repository, contract);

    @Test
    @DisplayName("cancelContract revokes the contract recorded on the application")
    void cancelContractRevokesTheContractRecordedOnTheApplication() {

        // given: an application that already carries an issued contract
        var application = testLeasingApplication().contractId(new ContractId("CONTRACT-1")).build();
        given(repository.findById(application.id())).willReturn(application);

        // when: the contract is cancelled as part of the compensation
        underTest.cancelContract(application.id());

        // then: the recorded contract id is revoked in the contract system
        then(repository).should().findById(application.id());
        then(contract).should().revokeContract(new ContractId("CONTRACT-1"));
        then(repository).shouldHaveNoMoreInteractions();
        then(contract).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("cancelContract fails when the application is unknown")
    void cancelContractFailsWhenTheApplicationIsUnknown() {

        // given: an id the repository cannot resolve
        var id = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000");
        given(repository.findById(id)).willReturn(null);

        // when/then: cancellation fails and no contract is touched
        assertThatThrownBy(() -> underTest.cancelContract(id))
            .isInstanceOf(IllegalStateException.class);
        then(repository).should().findById(id);
        then(repository).shouldHaveNoMoreInteractions();
        then(contract).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("cancelContract fails when no contract was issued for the application")
    void cancelContractFailsWhenNoContractWasIssuedForTheApplication() {

        // given: an application that never received a contract
        var application = testLeasingApplication().contractId(null).build();
        given(repository.findById(application.id())).willReturn(application);

        // when/then: cancellation fails and no contract is touched
        assertThatThrownBy(() -> underTest.cancelContract(application.id()))
            .isInstanceOf(IllegalStateException.class);
        then(repository).should().findById(application.id());
        then(repository).shouldHaveNoMoreInteractions();
        then(contract).shouldHaveNoMoreInteractions();
    }
}
