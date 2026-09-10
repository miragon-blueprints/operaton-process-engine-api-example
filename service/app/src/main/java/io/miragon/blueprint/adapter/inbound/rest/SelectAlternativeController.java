package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External completion of the {@code Clarify alternative with customer} user task — the counterpart to a
 * human completing the deployed Camunda Form in the Operaton Tasklist.
 */
@RestController
@RequestMapping("/api/bike-leasing")
public class SelectAlternativeController {

    private final SelectAlternativeUseCase useCase;

    public SelectAlternativeController(SelectAlternativeUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(operationId = "selectAlternative")
    @PostMapping("/{applicationId}/clarify-alternative")
    public ResponseEntity<Void> clarifyAlternative(
        @PathVariable String applicationId,
        @RequestBody AlternativeDecisionInput input
    ) {
        useCase.selectAlternative(
            new SelectAlternativeUseCase.Command(
                ApplicationId.of(applicationId),
                input.alternativeFound(),
                input.bikeId() != null ? new BikeId(input.bikeId()) : null,
                input.bikeModel()
            )
        );
        return ResponseEntity.accepted().build();
    }

    // `required = true` / `nullable = true` mirror the non-null contract Kotlin once carried in its type
    // system: swagger-core cannot derive them from JSpecify annotations (swagger-api/swagger-core#5001).
    // On this input record, required also restores the Kotlin-era behavior of rejecting a missing field with 400.
    public record AlternativeDecisionInput(
        @JsonProperty(value = "alternativeFound", required = true) boolean alternativeFound,
        @JsonProperty("bikeId") @Schema(nullable = true) @Nullable String bikeId,
        @JsonProperty("bikeModel") @Schema(nullable = true) @Nullable String bikeModel
    ) {

        @JsonCreator
        public AlternativeDecisionInput {
        }
    }
}
