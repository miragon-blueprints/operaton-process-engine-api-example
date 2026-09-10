package io.miragon.blueprint.adapter.inbound.operaton;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code orderBike} external task and returns the order outcome as process variables
 * ({@code orderId}, {@code bikeAvailable}), which the following gateway routes on.
 */
@Component
public class OrderBikeWorker {

    private final OrderBikeUseCase useCase;

    public OrderBikeWorker(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @ProcessEngineWorker(topic = ServiceTasks.ORDER_BIKE)
    public Map<String, @Nullable Object> orderBike(@Variable String applicationId) {
        OrderBikeUseCase.Result result = useCase.orderBike(ApplicationId.of(applicationId));
        Map<String, @Nullable Object> variables = new HashMap<>();
        variables.put(Variables.ServiceTaskOrderBike.ORDER_ID.getValue(),
            result.orderId() == null ? null : result.orderId().value());
        variables.put(Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.getValue(), result.bikeAvailable());
        return variables;
    }
}
