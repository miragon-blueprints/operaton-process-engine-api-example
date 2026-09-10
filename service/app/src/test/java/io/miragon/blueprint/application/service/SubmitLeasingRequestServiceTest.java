package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class SubmitLeasingRequestServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-15T10:30:00Z"), ZoneOffset.UTC);
    private final SubmitLeasingRequestService underTest =
        new SubmitLeasingRequestService(repository, bikePortfolio, process, clock);

    @Test
    @DisplayName("submit registers the bike, persists a received application and starts the process")
    void submitRegistersTheBikePersistsAReceivedApplicationAndStartsTheProcess() {

        // given: a leasing-request command and stubbed out-ports
        SubmitLeasingRequestUseCase.Command command =
            new SubmitLeasingRequestUseCase.Command(
                new CustomerName("John Doe"),
                new Email("john.doe@test.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                "Gravel Explorer 900");
        given(bikePortfolio.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the use case is invoked
        ApplicationId id = underTest.submit(command);

        // then: the bike is stored in the portfolio, a RECEIVED application referencing it is saved, and the process starts
        then(bikePortfolio).should().save(new Bike(new BikeId("BIKE-900"), "Gravel Explorer 900"));
        then(repository).should().save(
            argThat(it ->
                it.id().equals(id)
                    && it.status() == LeasingStatus.RECEIVED
                    && it.bikeId().equals(new BikeId("BIKE-900"))
                    && it.createdAt().equals(LocalDateTime.now(clock))));
        then(process).should().submitRequest(argThat(it -> it.id().equals(id)));
        then(repository).shouldHaveNoMoreInteractions();
        then(bikePortfolio).shouldHaveNoMoreInteractions();
        then(process).shouldHaveNoMoreInteractions();
    }
}
