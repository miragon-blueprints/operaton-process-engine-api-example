package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.miragon.blueprint.application.port.inbound.GetPendingClarificationsQuery;
import io.miragon.blueprint.domain.leasing.PendingClarification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/tasks/clarify-alternative} — the back-office inbox of applications waiting on the
 * alternative-clarification task.
 *
 * <p>The DTO carries NO task id on purpose. Completing a clarification goes through the domain
 * ({@code POST /api/bike-leasing/{id}/clarify-alternative}), which correlates by business key — so this
 * read model cannot be used to bypass the domain and complete a task directly. That is exactly the
 * lesson the form-only {@code clarify-return} task contrasts against.
 */
@RestController
@RequestMapping("/api/tasks")
public class GetPendingClarificationsController {

    private final GetPendingClarificationsQuery query;

    public GetPendingClarificationsController(GetPendingClarificationsQuery query) {
        this.query = query;
    }

    @Operation(operationId = "listPendingClarifications")
    @GetMapping("/clarify-alternative")
    public List<PendingClarificationDto> pending() {
        return query.pending().stream().map(GetPendingClarificationsController::toDto).toList();
    }

    private static PendingClarificationDto toDto(PendingClarification pending) {
        return new PendingClarificationDto(
            pending.applicationId().value().toString(),
            pending.customerName().value(),
            pending.requestedBikeId().value(),
            pending.requestedBikeModel(),
            pending.waitingSince()
        );
    }

    // `required = true` / `nullable = true` mirror the non-null contract Kotlin once carried in its
    // type system: swagger-core cannot derive them from JSpecify annotations (swagger-api/swagger-core#5001).
    public record PendingClarificationDto(
        @JsonProperty(required = true) String applicationId,
        @JsonProperty(required = true) String customerName,
        @JsonProperty(required = true) String requestedBikeId,
        @Schema(nullable = true) @Nullable String requestedBikeModel,
        // ISO-8601 string — see the note in GetLeasingApplicationController.LeasingApplicationDto.
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonProperty(required = true)
        LocalDateTime waitingSince
    ) {
    }
}
