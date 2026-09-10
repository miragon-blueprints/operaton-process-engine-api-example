package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class GetLeasingApplicationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final GetLeasingApplicationService underTest = new GetLeasingApplicationService(repository, bikePortfolio);

    @Test
    @DisplayName("byId returns the application with its bike model resolved from the portfolio")
    void byIdReturnsTheApplicationWithItsBikeModelResolvedFromThePortfolio() {

        // given: an application and its bike in the portfolio
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);
        given(bikePortfolio.findByBikeId(application.bikeId()))
            .willReturn(new Bike(application.bikeId(), "Gravel Explorer 900"));

        // when: the application is queried by id
        GetLeasingApplicationQuery.Result result = underTest.byId(application.id());

        // then: the application and the resolved bike model are returned
        assertThat(result.application()).isEqualTo(application);
        assertThat(result.bikeModel()).isEqualTo("Gravel Explorer 900");
        then(repository).should().findById(application.id());
        then(bikePortfolio).should().findByBikeId(application.bikeId());
        then(repository).shouldHaveNoMoreInteractions();
        then(bikePortfolio).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("byId returns null when the application does not exist")
    void byIdReturnsNullWhenTheApplicationDoesNotExist() {

        // given: an unknown application id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        given(repository.findById(id)).willReturn(null);

        // when / then: the query returns null and the portfolio is never consulted
        assertThat(underTest.byId(id)).isNull();
        then(repository).should().findById(id);
        then(repository).shouldHaveNoMoreInteractions();
        then(bikePortfolio).shouldHaveNoMoreInteractions();
    }
}
