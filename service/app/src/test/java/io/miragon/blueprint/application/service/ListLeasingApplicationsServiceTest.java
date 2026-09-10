package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.ListLeasingApplicationsQuery;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class ListLeasingApplicationsServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final ListLeasingApplicationsService underTest =
        new ListLeasingApplicationsService(repository, bikePortfolio);

    @Test
    @DisplayName("maps a page of applications and resolves each bike model in one batch query")
    void mapsAPageOfApplicationsAndResolvesEachBikeModelInOneBatchQuery() {
        // given: a page with two applications on different bikes
        var a = testLeasingApplication().bikeId(new BikeId("BIKE-900")).status(LeasingStatus.RECEIVED).build();
        var b = testLeasingApplication().bikeId(new BikeId("BIKE-800")).status(LeasingStatus.ACTIVE).build();
        given(repository.findAll(any()))
            .willReturn(new LeasingApplicationRepository.Page(List.of(a, b), 0, 20, 2, 1));
        given(bikePortfolio.findAllByIds(any())).willReturn(List.of(
            new Bike(new BikeId("BIKE-900"), "Gravel Explorer 900"),
            new Bike(new BikeId("BIKE-800"), "Carbon Road 800")
        ));

        // when: the first page is requested without a status filter
        var result = underTest.list(new ListLeasingApplicationsQuery.Filter(null, 0, 20));

        // then: paging metadata is preserved and each item carries its resolved model
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).bikeModel()).isEqualTo("Gravel Explorer 900");
        assertThat(result.items().get(1).bikeModel()).isEqualTo("Carbon Road 800");
        // and: exactly one batch lookup was issued for the bikes, not one per row
        then(bikePortfolio).should(times(1)).findAllByIds(List.of(new BikeId("BIKE-900"), new BikeId("BIKE-800")));
    }

    @Test
    @DisplayName("passes the status filter through to the repository criteria")
    void passesTheStatusFilterThroughToTheRepositoryCriteria() {
        // given: a repository that records the criteria it was asked for
        given(repository.findAll(any()))
            .willReturn(new LeasingApplicationRepository.Page(List.of(), 1, 5, 0, 0));
        given(bikePortfolio.findAllByIds(List.of())).willReturn(List.of());

        // when: a filtered, paged request is made
        underTest.list(new ListLeasingApplicationsQuery.Filter(LeasingStatus.ACTIVE, 1, 5));

        // then: the exact criteria reach the outbound port
        then(repository).should().findAll(new LeasingApplicationRepository.Criteria(LeasingStatus.ACTIVE, 1, 5));
        then(repository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("leaves the model null when the bike is not in the portfolio")
    void leavesTheModelNullWhenTheBikeIsNotInThePortfolio() {
        // given: an application whose bike is missing from the portfolio
        var orphan = testLeasingApplication().bikeId(new BikeId("BIKE-GONE")).build();
        given(repository.findAll(any()))
            .willReturn(new LeasingApplicationRepository.Page(List.of(orphan), 0, 20, 1, 1));
        given(bikePortfolio.findAllByIds(any())).willReturn(List.of());

        // when: the page is mapped
        var result = underTest.list(new ListLeasingApplicationsQuery.Filter(null, 0, 20));

        // then: the missing model surfaces as null rather than throwing
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).bikeModel()).isNull();
    }
}
