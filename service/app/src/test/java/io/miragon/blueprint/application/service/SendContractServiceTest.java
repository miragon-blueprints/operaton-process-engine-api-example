package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SendContractServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ContractPort contract = mock(ContractPort.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendContractService underTest = new SendContractService(repository, contract, notification);

    @Test
    @DisplayName("sendContract issues the contract, records its id on the application and notifies the customer")
    void sendContractIssuesTheContractRecordsItsIdOnTheApplicationAndNotifiesTheCustomer() {

        // given: an application whose contract the contract system will issue
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);
        given(contract.issueContract(application.id())).willReturn(new ContractId("CONTRACT-1"));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the contract is sent
        underTest.sendContract(application.id());

        // then: the contract is issued, its id is stored on the application and the customer is asked to sign
        then(repository).should().findById(application.id());
        then(contract).should().issueContract(application.id());
        then(repository).should().save(argThat(arg -> new ContractId("CONTRACT-1").equals(arg.contractId())));
        then(notification).should().send(any(), eq(application));
        then(repository).shouldHaveNoMoreInteractions();
        then(contract).shouldHaveNoMoreInteractions();
        then(notification).shouldHaveNoMoreInteractions();
    }
}
