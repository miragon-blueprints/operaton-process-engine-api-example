package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SelectAlternativeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final SelectAlternativeService underTest = new SelectAlternativeService(repository, bikePortfolio, process);

    @Test
    @DisplayName("an accepted alternative registers the new bike and hands it to the process")
    void anAcceptedAlternativeRegistersTheNewBikeAndHandsItToTheProcess() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);
        given(bikePortfolio.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: an alternative bike is selected
        underTest.selectAlternative(
            new SelectAlternativeUseCase.Command(application.id(), true, new BikeId("BIKE-ALT"), "Aero Road 700"));

        // then: the alternative is registered in the portfolio and the task is completed with it; the order step stores it
        then(repository).should().findById(application.id());
        then(bikePortfolio).should().save(new Bike(new BikeId("BIKE-ALT"), "Aero Road 700"));
        then(process).should().completeAlternativeClarification(application.id(), true, new BikeId("BIKE-ALT"));
        then(repository).shouldHaveNoMoreInteractions();
        then(bikePortfolio).shouldHaveNoMoreInteractions();
        then(process).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("no alternative completes the user task without touching the bike")
    void noAlternativeCompletesTheUserTaskWithoutTouchingTheBike() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);

        // when: no alternative is found
        underTest.selectAlternative(new SelectAlternativeUseCase.Command(application.id(), false, null, null));

        // then: neither the portfolio nor the application is touched, and the task is completed as declined
        then(repository).should().findById(application.id());
        then(process).should().completeAlternativeClarification(application.id(), false, null);
        then(repository).shouldHaveNoMoreInteractions();
        then(bikePortfolio).shouldHaveNoMoreInteractions();
        then(process).shouldHaveNoMoreInteractions();
    }
}
