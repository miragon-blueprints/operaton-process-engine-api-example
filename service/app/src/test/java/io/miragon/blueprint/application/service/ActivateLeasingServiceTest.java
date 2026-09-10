package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class ActivateLeasingServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ActivateLeasingService underTest = new ActivateLeasingService(repository);

    @Test
    @DisplayName("activate loads the application, activates it, and persists the ACTIVE status")
    void activateLoadsTheApplicationActivatesItAndPersistsTheActiveStatus() {

        // given: a handed-over application
        var id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        var application = testLeasingApplication().id(id).status(LeasingStatus.HANDED_OVER).build();
        given(repository.findById(id)).willReturn(application);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the leasing is activated
        underTest.activate(id);

        // then: the application is persisted with ACTIVE
        then(repository).should().findById(id);
        then(repository).should().save(argThat(saved -> saved.status() == LeasingStatus.ACTIVE));
        then(repository).shouldHaveNoMoreInteractions();
    }
}
