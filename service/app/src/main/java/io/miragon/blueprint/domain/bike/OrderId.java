package io.miragon.blueprint.domain.bike;

public record OrderId(String value) {

    public OrderId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("OrderId must not be blank");
        }
    }
}
