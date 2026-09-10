package io.miragon.blueprint.application.port.outbound;

import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface LeasingApplicationRepository {

    LeasingApplication save(LeasingApplication application);

    @Nullable
    LeasingApplication findById(ApplicationId id);

    /**
     * Reads a page of applications, optionally filtered by status. The {@link Criteria} and {@link Page} are the
     * port's own types on purpose — Spring Data's {@code Pageable}/{@code Page} stop at the persistence adapter so
     * the application layer never depends on a persistence technology.
     */
    Page findAll(Criteria criteria);

    record Criteria(
        @Nullable LeasingStatus status,
        int page,
        int size
    ) {
    }

    record Page(
        List<LeasingApplication> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }
}
