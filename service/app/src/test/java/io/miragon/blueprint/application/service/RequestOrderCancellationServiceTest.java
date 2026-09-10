package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class RequestOrderCancellationServiceTest {

    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final RequestOrderCancellationService underTest = new RequestOrderCancellationService(bikeDealer);

    @Test
    @DisplayName("requestCancellation asks the dealer and returns its answer")
    void requestCancellationAsksTheDealerAndReturnsItsAnswer() {
        // given: a placed order the dealer allows cancelling
        OrderId orderId = new OrderId("ORDER-900");
        given(bikeDealer.requestCancellation(orderId)).willReturn(true);
        // when: the dealer is asked whether it can be cancelled
        boolean possible = underTest.requestCancellation(orderId);
        // then: the dealer's answer is returned
        assertThat(possible).isTrue();
        then(bikeDealer).should().requestCancellation(orderId);
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("requestCancellation returns false when the dealer refuses")
    void requestCancellationReturnsFalseWhenTheDealerRefuses() {
        // given: an order the dealer will not allow cancelling
        OrderId orderId = new OrderId("ORDER-901");
        given(bikeDealer.requestCancellation(orderId)).willReturn(false);
        // when: the dealer is asked whether it can be cancelled
        boolean possible = underTest.requestCancellation(orderId);
        // then: the dealer's negative answer is propagated unchanged
        assertThat(possible).isFalse();
        then(bikeDealer).should().requestCancellation(orderId);
        then(bikeDealer).shouldHaveNoMoreInteractions();
    }
}
