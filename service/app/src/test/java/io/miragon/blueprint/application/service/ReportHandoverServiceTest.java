package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

public class ReportHandoverServiceTest {

    private final LeasingProcess process = mock(LeasingProcess.class);
    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ReportHandoverService underTest = new ReportHandoverService(process, repository);

    @Test
    @DisplayName("reportHandover correlates the message and persists the HANDED_OVER status")
    public void reportHandoverCorrelatesTheMessageAndPersistsTheHandedOverStatus() {

        // given: an ordered application
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        LeasingApplication application = testLeasingApplication().id(id).status(LeasingStatus.ORDERED).build();
        given(repository.findById(id)).willReturn(application);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the handover is reported
        underTest.reportHandover(id);

        // then: the message is correlated and the application is persisted with HANDED_OVER
        then(process).should().correlateHandoverReported(id);
        then(repository).should().findById(id);
        then(repository).should().save(argThat(it -> it.status() == LeasingStatus.HANDED_OVER));
        then(process).shouldHaveNoMoreInteractions();
        then(repository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("reportHandover does not persist when correlation fails")
    public void reportHandoverDoesNotPersistWhenCorrelationFails() {

        // given: a correlation that throws
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));
        willThrow(new RuntimeException("no token")).given(process).correlateHandoverReported(id);

        // when / then: the exception propagates without touching the repository
        try {
            underTest.reportHandover(id);
        } catch (RuntimeException ignored) {
        }

        then(process).should().correlateHandoverReported(id);
        then(repository).should(never()).findById(any());
        then(repository).should(never()).save(any());
        then(process).shouldHaveNoMoreInteractions();
        then(repository).shouldHaveNoMoreInteractions();
    }
}
