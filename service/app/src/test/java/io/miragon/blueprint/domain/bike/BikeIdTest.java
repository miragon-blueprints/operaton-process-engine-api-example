package io.miragon.blueprint.domain.bike;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BikeIdTest {

    @Test
    @DisplayName("exposes the wrapped bike id")
    void exposesTheWrappedBikeId() {
        // given/when: a bike id is created from a non-blank value
        BikeId bikeId = new BikeId("BIKE-900");
        // then: the raw value is exposed unchanged
        assertThat(bikeId.value()).isEqualTo("BIKE-900");
    }

    @Test
    @DisplayName("rejects a blank bike id")
    void rejectsABlankBikeId() {
        // when/then: a blank value is refused
        assertThatThrownBy(() -> new BikeId("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
