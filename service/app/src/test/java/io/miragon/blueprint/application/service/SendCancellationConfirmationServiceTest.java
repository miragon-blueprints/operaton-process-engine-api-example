package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SendCancellationConfirmationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendCancellationConfirmationService underTest =
        new SendCancellationConfirmationService(repository, notification);

    @Test
    @DisplayName("sendCancellationConfirmation confirms to the customer and marks the application cancelled")
    void sendCancellationConfirmationConfirmsToTheCustomerAndMarksTheApplicationCancelled() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the cancellation confirmation is sent
        underTest.sendCancellationConfirmation(application.id());

        // then: the customer is informed and the application is moved to CANCELLED
        ArgumentCaptor<LeasingApplication> saved = ArgumentCaptor.forClass(LeasingApplication.class);
        then(repository).should().findById(application.id());
        then(notification).should().send(any(), eq(application));
        then(repository).should().save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(LeasingStatus.CANCELLED);
        then(repository).shouldHaveNoMoreInteractions();
        then(notification).shouldHaveNoMoreInteractions();
    }
}
