package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the {@code Clarify alternative with customer} user task from the outside — the "external"
 * completion path via our own client, next to a human completing the deployed Camunda Form in the
 * Tasklist. When an alternative bike was found, the newly chosen bike is carried into the re-order.
 */
public interface SelectAlternativeUseCase {
    void selectAlternative(Command command);

    record Command(
        ApplicationId applicationId,
        boolean alternativeFound,
        @Nullable BikeId bikeId,
        @Nullable String bikeModel
    ) {

        public Command(ApplicationId applicationId, boolean alternativeFound) {
            this(applicationId, alternativeFound, null, null);
        }
    }
}
