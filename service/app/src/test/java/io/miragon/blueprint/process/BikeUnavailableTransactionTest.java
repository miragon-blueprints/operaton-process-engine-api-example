package io.miragon.blueprint.process;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static io.miragon.blueprint.process.util.JobExecutionUtils.continueToNextWaitState;
import static io.miragon.blueprint.process.util.ProcessInstanceUtils.findProcessInstance;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

/**
 * Runs the out-of-stock order with the <strong>real</strong> use cases instead of mocks. The
 * {@code @Transactional} order service throws the {@code BikeUnavailableException} out of its transaction;
 * that must not keep the {@code @ProcessEngineWorker} from reporting the {@code bikeUnavailable} BPMN error
 * to the engine — otherwise the order task would fail into an incident — and it must not undo the bike the
 * service stored before.
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
    private TaskService taskService;

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

    @Test
    @DisplayName("alternative via tasklist - the bike the form submits is ordered and stored on the application")
    public void alternativeViaTasklistTheBikeTheFormSubmitsIsOrderedAndStoredOnTheApplication() {
        LeasingApplication application = receivedApplication(new BikeId("BIKE-OOS"));
        repository.save(application);
        process.submitRequest(application);
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());
        signContractAndRunIntoTheUnavailableBike(application.id());

        // the Tasklist completes the task with the variables its Camunda Form submits, bypassing the domain endpoint
        Task task =
            taskService
                .createTaskQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue())
                .singleResult();
        taskService.complete(
            task.getId(),
            Map.of(
                Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.getValue(), true,
                Variables.UserTaskClarifyAlternative.BIKE_ID.getValue(), "BIKE-900"));
        continueToNextWaitState(processEngine); // re-order succeeds -> parallel join -> handover wait state

        assertThat(instance).isWaitingAt(Elements.EVENT_HANDOVER_REPORTED.getValue());
        LeasingApplication stored = repository.findById(application.id());
        Assertions.assertThat(stored.bikeId()).isEqualTo(new BikeId("BIKE-900"));
        Assertions.assertThat(stored.status()).isEqualTo(LeasingStatus.ORDERED);
    }

    @Test
    @DisplayName("bike unavailable - the bike the process carries stays on the application although the order fails")
    public void bikeUnavailableTheBikeTheProcessCarriesStaysOnTheApplicationAlthoughTheOrderFails() {
        LeasingApplication application = receivedApplication(new BikeId("BIKE-900"));
        repository.save(application);
        process.submitRequest(application.selectAlternative(new BikeId("BIKE-OOS")));
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());

        signContractAndRunIntoTheUnavailableBike(application.id());

        assertThat(instance).isWaitingAt(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue());
        Assertions.assertThat(repository.findById(application.id()).bikeId()).isEqualTo(new BikeId("BIKE-OOS"));
    }

    private void signContractAndRunIntoTheUnavailableBike(ApplicationId id) {
        continueToNextWaitState(processEngine); // parks on the signature wait state
        process.correlateContractSigned(id);
        continueToNextWaitState(processEngine); // fork -> order raises bikeUnavailable -> parks on clarify-alternative
    }

    private LeasingApplication receivedApplication(BikeId bikeId) {
        return LeasingApplication.receive(
            ApplicationId.newId(),
            new CustomerName("Test Customer"),
            new Email("test@example.com"),
            35,
            3500.0,
            bikeId,
            LocalDateTime.now());
    }
}
