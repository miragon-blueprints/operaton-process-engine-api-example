package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.miragon.blueprint.application.port.inbound.ListLeasingApplicationsQuery;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/bike-leasing?status=&page=&size=} — the customer-portal list. A separate controller from
 * the paging query keeps to the "one inbound port per controller" rule the architecture tests enforce.
 */
@RestController
@RequestMapping("/api/bike-leasing")
public class ListLeasingApplicationsController {

    private final ListLeasingApplicationsQuery query;

    public ListLeasingApplicationsController(ListLeasingApplicationsQuery query) {
        this.query = query;
    }

    @Operation(operationId = "listLeasingApplications")
    @GetMapping
    public LeasingApplicationPageDto list(
        @RequestParam(required = false) @Nullable String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ListLeasingApplicationsQuery.Filter filter = new ListLeasingApplicationsQuery.Filter(
            status != null ? parseStatus(status) : null,
            page,
            size);
        return toDto(query.list(filter));
    }

    private static LeasingStatus parseStatus(String raw) {
        try {
            return LeasingStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "unknown status '" + raw + "'; expected one of "
                    + Arrays.stream(LeasingStatus.values()).map(Enum::name).collect(Collectors.joining(", ")),
                e);
        }
    }

    private static LeasingApplicationPageDto toDto(ListLeasingApplicationsQuery.Page result) {
        return new LeasingApplicationPageDto(
            result.items().stream().map(ListLeasingApplicationsController::toDto).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages());
    }

    private static LeasingApplicationSummaryDto toDto(ListLeasingApplicationsQuery.Item item) {
        return new LeasingApplicationSummaryDto(
            item.applicationId().value().toString(),
            item.customerName().value(),
            item.bikeId().value(),
            item.bikeModel(),
            item.status().name(),
            item.createdAt());
    }

    // `required = true` / `nullable = true` mirror the non-null contract Kotlin once carried in its
    // type system: swagger-core cannot derive them from JSpecify annotations (swagger-api/swagger-core#5001).
    public record LeasingApplicationPageDto(
        @JsonProperty(required = true) List<LeasingApplicationSummaryDto> items,
        @JsonProperty(required = true) int page,
        @JsonProperty(required = true) int size,
        @JsonProperty(required = true) long totalElements,
        @JsonProperty(required = true) int totalPages
    ) {
    }

    public record LeasingApplicationSummaryDto(
        @JsonProperty(required = true) String applicationId,
        @JsonProperty(required = true) String customerName,
        @JsonProperty(required = true) String bikeId,
        @Schema(nullable = true) @Nullable String bikeModel,
        @JsonProperty(required = true) String status,
        // ISO-8601 string — see the note in GetLeasingApplicationController.LeasingApplicationDto.
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonProperty(required = true)
        LocalDateTime createdAt
    ) {
    }
}
