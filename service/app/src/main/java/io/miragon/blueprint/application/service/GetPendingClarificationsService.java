package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.GetPendingClarificationsQuery;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.PendingClarification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetPendingClarificationsService implements GetPendingClarificationsQuery {

    private final TaskInboxPort taskInbox;
    private final LeasingApplicationRepository repository;
    private final BikePortfolioRepository bikePortfolio;

    public GetPendingClarificationsService(
        TaskInboxPort taskInbox,
        LeasingApplicationRepository repository,
        BikePortfolioRepository bikePortfolio
    ) {
        this.taskInbox = taskInbox;
        this.repository = repository;
        this.bikePortfolio = bikePortfolio;
    }

    @Override
    public List<PendingClarification> pending() {
        List<TaskInboxPort.OpenClarification> openTasks = taskInbox.findOpenClarifications();
        // Pair each open task with its application; skip tasks whose application vanished (defensive).
        List<Map.Entry<TaskInboxPort.OpenClarification, LeasingApplication>> cases = new ArrayList<>();
        for (TaskInboxPort.OpenClarification task : openTasks) {
            LeasingApplication application = repository.findById(task.applicationId());
            if (application != null) {
                cases.add(Map.entry(task, application));
            }
        }
        List<BikeId> bikeIds = cases.stream().map(entry -> entry.getValue().bikeId()).toList();
        Map<BikeId, String> models = new HashMap<>();
        for (Bike bike : bikePortfolio.findAllByIds(bikeIds)) {
            models.put(bike.bikeId(), bike.model());
        }
        List<PendingClarification> result = new ArrayList<>();
        for (Map.Entry<TaskInboxPort.OpenClarification, LeasingApplication> entry : cases) {
            LeasingApplication application = entry.getValue();
            result.add(new PendingClarification(
                application.id(),
                application.customerName(),
                application.bikeId(),
                models.get(application.bikeId()),
                entry.getKey().waitingSince()
            ));
        }
        return result;
    }
}
