package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

public class OrderBikeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final OrderBikeService underTest = new OrderBikeService(repository, bikeDealer);

    @Test
    @DisplayName("orderBike places an order when the dealer has the bike in stock")
    public void orderBikePlacesAnOrderWhenTheDealerHasTheBikeInStock() {

        // given: an application whose bike is available at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        given(repository.findById(application.id())).willReturn(application);
        given(bikeDealer.checkAvailability(application.bikeId())).willReturn(true);
        given(bikeDealer.order(application.bikeId())).willReturn(new OrderId("ORDER-900"));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the bike is ordered
        OrderBikeUseCase.Result result = underTest.orderBike(application.id());

        // then: the order id is returned and the application moves to ORDERED
        assertThat(result.bikeAvailable()).isTrue();
        assertThat(result.orderId()).isEqualTo(new OrderId("ORDER-900"));
        then(bikeDealer).should().checkAvailability(application.bikeId());
        then(bikeDealer).should().order(application.bikeId());
        then(repository).should().save(argThat(it ->
                it.status() == LeasingStatus.ORDERED && new OrderId("ORDER-900").equals(it.orderId())));
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("orderBike reports an out-of-stock bike as unavailable and places no order")
    public void orderBikeReportsAnOutOfStockBikeAsUnavailableAndPlacesNoOrder() {

        // given: an application whose bike is out of stock at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        given(repository.findById(application.id())).willReturn(application);
        given(bikeDealer.checkAvailability(application.bikeId())).willReturn(false);

        // when: the bike is ordered
        OrderBikeUseCase.Result result = underTest.orderBike(application.id());

        // then: no order is placed and the bike is reported unavailable
        assertThat(result.bikeAvailable()).isFalse();
        assertThat(result.orderId()).isNull();
        then(bikeDealer).should().checkAvailability(application.bikeId());
        then(bikeDealer).should(never()).order(any());
        then(repository).should(never()).save(any());
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }
}
