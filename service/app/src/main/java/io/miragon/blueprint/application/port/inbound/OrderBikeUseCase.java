package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.jspecify.annotations.Nullable;

public interface OrderBikeUseCase {
    Result orderBike(ApplicationId id);

    record Result(
        // null when the requested bike was out of stock and no order was placed
        @Nullable OrderId orderId,
        boolean bikeAvailable
    ) {
    }
}
