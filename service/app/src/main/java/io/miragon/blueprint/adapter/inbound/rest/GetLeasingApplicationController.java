package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class GetLeasingApplicationController {

    private final GetLeasingApplicationQuery query;

    public GetLeasingApplicationController(GetLeasingApplicationQuery query) {
        this.query = query;
    }

    @Operation(operationId = "getLeasingApplication")
    @GetMapping("/{applicationId}")
    public ResponseEntity<LeasingApplicationDto> byId(@PathVariable String applicationId) {
        GetLeasingApplicationQuery.Result result = query.byId(ApplicationId.of(applicationId));
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(result));
    }

    public record LeasingApplicationDto(
        String applicationId,
        String customerName,
        String email,
        int age,
        double monthlyNetIncome,
        String bikeId,
        @Nullable String bikeModel,
        String status,
        @Nullable String orderId,
        @Nullable String contractId,
        // Force ISO-8601 string form: Jackson 3 (SB4) defaults to a numeric array, but the Operaton
        // webapp serves /api with its own Jackson mapper that ignores our global date-time config, so
        // the format is pinned at the field to keep the payload in sync with the springdoc contract
        // any API consumer relies on.
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime createdAt
    ) {
    }

    private static LeasingApplicationDto toDto(GetLeasingApplicationQuery.Result result) {
        LeasingApplication application = result.application();
        OrderId orderId = application.orderId();
        ContractId contractId = application.contractId();
        return new LeasingApplicationDto(
            application.id().value().toString(),
            application.customerName().value(),
            application.email().value(),
            application.age(),
            application.monthlyNetIncome(),
            application.bikeId().value(),
            // resolved from the bike portfolio, not carried on the application
            result.bikeModel(),
            application.status().name(),
            orderId != null ? orderId.value() : null,
            contractId != null ? contractId.value() : null,
            application.createdAt());
    }
}
