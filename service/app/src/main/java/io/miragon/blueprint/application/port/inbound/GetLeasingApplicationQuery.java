package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.jspecify.annotations.Nullable;

public interface GetLeasingApplicationQuery {
    @Nullable Result byId(ApplicationId id);

    /** The application together with the model of its bike, resolved from the portfolio. */
    record Result(
        LeasingApplication application,
        @Nullable String bikeModel
    ) {
    }
}
