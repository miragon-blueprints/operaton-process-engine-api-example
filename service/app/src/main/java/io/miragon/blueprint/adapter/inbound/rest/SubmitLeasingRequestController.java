package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class SubmitLeasingRequestController {

    private static final Logger log = LoggerFactory.getLogger(SubmitLeasingRequestController.class);

    private final SubmitLeasingRequestUseCase useCase;

    public SubmitLeasingRequestController(SubmitLeasingRequestUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(operationId = "submitLeasingRequest")
    @PostMapping
    public ResponseEntity<LeasingApplicationCreatedDto> submit(@RequestBody LeasingRequestInput input) {
        log.debug("Received leasing request: {}", input);
        ApplicationId id = useCase.submit(toCommand(input));
        return ResponseEntity.ok(new LeasingApplicationCreatedDto(id.value().toString()));
    }

    // `required = true` mirrors the non-null contract Kotlin once carried in its type system: swagger-core
    // cannot derive it from JSpecify annotations (swagger-api/swagger-core#5001). On this input record it
    // also restores the Kotlin-era deserialization behavior — a missing field is rejected with 400.
    public record LeasingRequestInput(
        @JsonProperty(value = "customerName", required = true) String customerName,
        @JsonProperty(value = "email", required = true) String email,
        @JsonProperty(value = "age", required = true) int age,
        @JsonProperty(value = "monthlyNetIncome", required = true) double monthlyNetIncome,
        @JsonProperty(value = "bikeId", required = true) String bikeId,
        @JsonProperty(value = "bikeModel", required = true) String bikeModel
    ) {
        @JsonCreator
        public LeasingRequestInput {
        }
    }

    public record LeasingApplicationCreatedDto(@JsonProperty(required = true) String applicationId) {
    }

    private static SubmitLeasingRequestUseCase.Command toCommand(LeasingRequestInput input) {
        return new SubmitLeasingRequestUseCase.Command(
            new CustomerName(input.customerName()),
            new Email(input.email()),
            input.age(),
            input.monthlyNetIncome(),
            new BikeId(input.bikeId()),
            input.bikeModel());
    }
}
