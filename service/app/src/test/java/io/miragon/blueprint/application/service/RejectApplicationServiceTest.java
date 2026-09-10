package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

public class RejectApplicationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final RejectApplicationService underTest = new RejectApplicationService(repository, notification);

    @Test
    @DisplayName("reject notifies the customer and persists the rejected status")
    public void rejectNotifiesTheCustomerAndPersistsTheRejectedStatus() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the application is rejected
        underTest.reject(application.id());

        // then: the application is loaded, the customer notified and the application saved as REJECTED
        then(repository).should().findById(application.id());
        then(notification).should().send(any(), eq(application));
        then(repository).should().save(argThat(it -> it.status() == LeasingStatus.REJECTED));
        then(repository).shouldHaveNoMoreInteractions();
        then(notification).shouldHaveNoMoreInteractions();
    }
}
