package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class BookCancellationCostsServiceTest {

    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final BookCancellationCostsService underTest = new BookCancellationCostsService(bikeDealer);

    @Test
    @DisplayName("bookCosts delegates the cost booking to the dealer")
    void bookCostsDelegatesTheCostBookingToTheDealer() {
        // given: a placed order
        var orderId = new OrderId("ORDER-900");
        // when: the costs are booked
        underTest.bookCosts(orderId);
        // then: the dealer out-port books the cancellation costs
        then(bikeDealer).should().bookCancellationCosts(orderId);
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }
}
