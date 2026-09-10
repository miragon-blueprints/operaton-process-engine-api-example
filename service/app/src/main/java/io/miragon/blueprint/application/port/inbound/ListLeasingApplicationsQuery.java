package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reads a page of leasing applications for the customer-portal list, optionally filtered by status.
 * The port owns its {@link Filter}/{@link Item}/{@link Page} types; Spring Data paging never crosses into the
 * application layer.
 */
public interface ListLeasingApplicationsQuery {
    Page list(Filter filter);

    record Filter(
        @Nullable LeasingStatus status,
        int page,
        int size
    ) {
    }

    record Item(
        ApplicationId applicationId,
        CustomerName customerName,
        BikeId bikeId,
        @Nullable String bikeModel,
        LeasingStatus status,
        LocalDateTime createdAt
    ) {
    }

    record Page(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }
}
