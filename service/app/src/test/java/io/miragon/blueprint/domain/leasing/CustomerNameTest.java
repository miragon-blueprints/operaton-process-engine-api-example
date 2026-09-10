package io.miragon.blueprint.domain.leasing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerNameTest {

    @Test
    @DisplayName("exposes the wrapped name")
    void exposesTheWrappedName() {
        // given/when: a customer name is created from a non-blank value
        var name = new CustomerName("John Doe");
        // then: the raw value is exposed unchanged
        assertThat(name.value()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("rejects an empty name")
    void rejectsAnEmptyName() {
        // when/then: an empty value is refused
        assertThatThrownBy(() -> new CustomerName(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a blank name")
    void rejectsABlankName() {
        // when/then: a whitespace-only value is refused
        assertThatThrownBy(() -> new CustomerName("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
