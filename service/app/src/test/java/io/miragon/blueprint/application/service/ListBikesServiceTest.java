package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.ListBikesQuery;
import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ListBikesServiceTest {

    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final ListBikesService underTest = new ListBikesService(bikePortfolio, bikeDealer);

    @Test
    @DisplayName("enriches each catalogue bike with its dealer availability")
    void enrichesEachCatalogueBikeWithItsDealerAvailability() {
        // given: a catalogue (already ordered by the portfolio) where one bike is out of stock
        given(bikePortfolio.findAll()).willReturn(List.of(
            new Bike(new BikeId("BIKE-900"), "Gravel Explorer 900"),
            new Bike(new BikeId("BIKE-OOS"), "Mountain Trail 600")
        ));
        given(bikeDealer.checkAvailability(new BikeId("BIKE-900"))).willReturn(true);
        given(bikeDealer.checkAvailability(new BikeId("BIKE-OOS"))).willReturn(false);

        // when: the catalogue is listed
        var result = underTest.all();

        // then: the portfolio's order is preserved and each item's availability comes from the dealer
        assertThat(result.stream().map(item -> item.bikeId().value()).toList())
            .containsExactly("BIKE-900", "BIKE-OOS");
        assertThat(singleByBikeId(result, "BIKE-900").available()).isTrue();
        assertThat(singleByBikeId(result, "BIKE-OOS").available()).isFalse();
    }

    @Test
    @DisplayName("returns an empty list for an empty catalogue")
    void returnsAnEmptyListForAnEmptyCatalogue() {
        // given: no bikes
        given(bikePortfolio.findAll()).willReturn(List.of());

        // when / then: nothing is listed and the dealer is never consulted
        assertThat(underTest.all()).isEmpty();
    }

    private static ListBikesQuery.Item singleByBikeId(List<ListBikesQuery.Item> items, String bikeId) {
        return items.stream().filter(item -> item.bikeId().value().equals(bikeId)).findFirst().orElseThrow();
    }
}
