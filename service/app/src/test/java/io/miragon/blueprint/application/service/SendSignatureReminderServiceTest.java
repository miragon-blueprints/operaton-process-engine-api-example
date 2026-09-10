package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SendSignatureReminderServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendSignatureReminderService underTest = new SendSignatureReminderService(repository, notification);

    @Test
    @DisplayName("sendSignatureReminder loads the application and reminds the customer")
    void sendSignatureReminderLoadsTheApplicationAndRemindsTheCustomer() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);

        // when: the signature reminder is sent
        underTest.sendSignatureReminder(application.id());

        // then: the application is loaded and the customer is reminded
        then(repository).should().findById(application.id());
        then(notification).should().send(any(), eq(application));
        then(repository).shouldHaveNoMoreInteractions();
        then(notification).shouldHaveNoMoreInteractions();
    }
}
