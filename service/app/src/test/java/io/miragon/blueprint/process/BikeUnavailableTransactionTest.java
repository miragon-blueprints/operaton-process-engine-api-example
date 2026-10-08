package io.miragon.blueprint.process;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static io.miragon.blueprint.process.util.JobExecutionUtils.continueToNextWaitState;
import static io.miragon.blueprint.process.util.ProcessInstanceUtils.findProcessInstance;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

/**
 * Runs the out-of-stock order with the <strong>real</strong> use cases instead of mocks. The
 * {@code @Transactional} order service rolls its transaction back when it throws the
 * {@code BikeUnavailableException}; that must not keep the {@code @ProcessEngineWorker} from reporting the
 * {@code bikeUnavailable} BPMN error to the engine — otherwise the order task would fail into an incident.
 * {@link BikeLeasingProcessTest} mocks the use cases and therefore cannot see this.
 */
@SpringBootTest
@ActiveProfiles("test")
public class BikeUnavailableTransactionTest {

    @Autowired
    private LeasingProcess process;

    @Autowired
    private LeasingApplicationRepository repository;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ProcessEngine processEngine;

    @BeforeEach
    public void setUp() {
        init(processEngine);
    }

    @Test
    @DisplayName("bike unavailable - the BPMN error raised by the real order service reaches the engine")
    public void bikeUnavailableTheBpmnErrorRaisedByTheRealOrderServiceReachesTheEngine() {
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        LeasingApplication application =
            LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                35,
                3500.0,
                new BikeId("BIKE-OOS"),
                LocalDateTime.now());
        repository.save(application);
        process.submitRequest(application);
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());

        continueToNextWaitState(processEngine); // parks on the signature wait state
        process.correlateContractSigned(application.id());
        continueToNextWaitState(processEngine); // fork -> order raises bikeUnavailable -> parks on clarify-alternative

        assertThat(instance)
            .isWaitingAt(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue())
            .hasPassed(Elements.EVENT_BIKE_UNAVAILABLE.getValue());
        Assertions.assertThat(runtimeService.createIncidentQuery().processInstanceId(instance.getId()).count())
            .isZero();
    }
}
