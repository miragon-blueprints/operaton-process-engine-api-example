package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        OrderId orderId = underTest.orderBike(application.id(), application.bikeId());

        // then: the order id is returned and the application moves to ORDERED
        assertThat(orderId).isEqualTo(new OrderId("ORDER-900"));
        then(bikeDealer).should().checkAvailability(application.bikeId());
        then(bikeDealer).should().order(application.bikeId());
        then(repository).should().save(argThat(it ->
                it.status() == LeasingStatus.ORDERED && new OrderId("ORDER-900").equals(it.orderId())));
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("orderBike orders the bike the process carries and stores it on the application")
    public void orderBikeOrdersTheBikeTheProcessCarriesAndStoresItOnTheApplication() {

        // given: an application still pointing at the bike that was requested first
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        BikeId alternative = new BikeId("BIKE-ALT");
        given(repository.findById(application.id())).willReturn(application);
        given(bikeDealer.checkAvailability(alternative)).willReturn(true);
        given(bikeDealer.order(alternative)).willReturn(new OrderId("ORDER-ALT"));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when: the process asks for the alternative bike
        OrderId orderId = underTest.orderBike(application.id(), alternative);

        // then: the alternative is checked, ordered and stored together with the order
        assertThat(orderId).isEqualTo(new OrderId("ORDER-ALT"));
        then(bikeDealer).should().checkAvailability(alternative);
        then(bikeDealer).should().order(alternative);
        then(repository).should().save(argThat(it ->
                it.bikeId().equals(alternative) && new OrderId("ORDER-ALT").equals(it.orderId())));
        then(repository).should().findById(application.id());
        then(bikeDealer).shouldHaveNoMoreInteractions();
        then(repository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("orderBike reports an out-of-stock bike as unavailable and places no order")
    public void orderBikeReportsAnOutOfStockBikeAsUnavailableAndPlacesNoOrder() {

        // given: an application whose bike is out of stock at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        given(repository.findById(application.id())).willReturn(application);
        given(bikeDealer.checkAvailability(application.bikeId())).willReturn(false);

        // when / then: ordering reports the bike as unavailable and places no order
        assertThatThrownBy(() -> underTest.orderBike(application.id(), application.bikeId()))
            .isInstanceOf(BikeUnavailableException.class)
            .hasMessage("Bike BIKE-OOS is not available at the dealer");
        then(bikeDealer).should().checkAvailability(application.bikeId());
        then(bikeDealer).should(never()).order(any());
        then(repository).should(never()).save(argThat(it -> it.orderId() != null));
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("orderBike keeps an unavailable alternative on the application")
    public void orderBikeKeepsAnUnavailableAlternativeOnTheApplication() {

        // given: an application whose alternative is out of stock as well
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        BikeId alternative = new BikeId("BIKE-OOS");
        given(repository.findById(application.id())).willReturn(application);
        given(bikeDealer.checkAvailability(alternative)).willReturn(false);

        // when / then: the alternative is reported as unavailable, yet the application points at it
        assertThatThrownBy(() -> underTest.orderBike(application.id(), alternative))
            .isInstanceOf(BikeUnavailableException.class)
            .hasMessage("Bike BIKE-OOS is not available at the dealer");
        then(repository).should().save(argThat(it -> it.bikeId().equals(alternative) && it.orderId() == null));
        then(bikeDealer).should(never()).order(any());
    }

    @Test
    @DisplayName("orderBike fails for an unknown application without asking the dealer")
    public void orderBikeFailsForAnUnknownApplicationWithoutAskingTheDealer() {

        // given: an id no application is stored for
        ApplicationId unknownId = ApplicationId.newId();
        given(repository.findById(unknownId)).willReturn(null);

        // when / then: ordering fails with the unknown-application message, nothing is ordered or saved
        assertThatThrownBy(() -> underTest.orderBike(unknownId, new BikeId("BIKE-900")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + unknownId);
        then(repository).should().findById(unknownId);
        then(repository).shouldHaveNoMoreInteractions();
        then(bikeDealer).shouldHaveNoInteractions();
    }
}
