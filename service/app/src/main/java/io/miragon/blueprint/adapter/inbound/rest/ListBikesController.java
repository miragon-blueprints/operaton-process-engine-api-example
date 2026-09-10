package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.miragon.blueprint.application.port.inbound.ListBikesQuery;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /api/bikes} — the bike catalogue for the submit form's picker. Exists so nobody has to type a
 * bike id by hand; unavailable bikes (e.g. {@code BIKE-OOS}) stay in the list on purpose, so a user can
 * drive the bike-unavailable scenario from the UI.
 */
@RestController
@RequestMapping("/api/bikes")
public class ListBikesController {

    private final ListBikesQuery query;

    public ListBikesController(ListBikesQuery query) {
        this.query = query;
    }

    @Operation(operationId = "listBikes")
    @GetMapping
    public List<BikeDto> all() {
        return query.all().stream()
            .map(item -> new BikeDto(item.bikeId().value(), item.model(), item.available()))
            .toList();
    }

    // `required = true` mirrors the non-null contract Kotlin once carried in its type system:
    // swagger-core cannot derive required/nullable from JSpecify annotations (swagger-api/swagger-core#5001).
    public record BikeDto(
        @JsonProperty(required = true) String bikeId,
        @JsonProperty(required = true) String model,
        @JsonProperty(required = true) boolean available
    ) {
    }
}
