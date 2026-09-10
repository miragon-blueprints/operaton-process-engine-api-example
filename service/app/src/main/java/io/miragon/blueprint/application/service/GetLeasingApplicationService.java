package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetLeasingApplicationService implements GetLeasingApplicationQuery {

    private final LeasingApplicationRepository repository;
    private final BikePortfolioRepository bikePortfolio;

    public GetLeasingApplicationService(LeasingApplicationRepository repository, BikePortfolioRepository bikePortfolio) {
        this.repository = repository;
        this.bikePortfolio = bikePortfolio;
    }

    @Override
    public GetLeasingApplicationQuery.@Nullable Result byId(ApplicationId id) {
        LeasingApplication application = repository.findById(id);
        if (application == null) {
            return null;
        }
        Bike bike = bikePortfolio.findByBikeId(application.bikeId());
        return new GetLeasingApplicationQuery.Result(application, bike != null ? bike.model() : null);
    }
}
