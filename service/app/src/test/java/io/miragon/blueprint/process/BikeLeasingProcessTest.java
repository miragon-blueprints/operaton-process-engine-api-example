package io.miragon.blueprint.process;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static io.miragon.blueprint.process.util.JobExecutionUtils.continueToNextWaitState;
import static io.miragon.blueprint.process.util.ProcessInstanceUtils.findProcessInstance;
import static io.miragon.blueprint.process.util.TimerUtils.fireTimer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

/**
 * Drives the deployed model end-to-end. Unlike the classic-delegate blueprint, the service tasks are
 * external tasks completed by the real, asynchronously polling {@code @ProcessEngineWorker} beans (which
 * call the mocked use cases). Each step therefore drives the process with
 * {@link io.miragon.blueprint.process.util.JobExecutionUtils#continueToNextWaitState continueToNextWaitState},
 * which fires async continuations <em>and</em> waits for the workers to drain the open external tasks, then
 * correlates messages / fires timers / completes the user task through the {@link LeasingProcess} port.
 */
@SpringBootTest
@ActiveProfiles("test")
public class BikeLeasingProcessTest {

    @Autowired
    private LeasingProcess process;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProcessEngine processEngine;

    @MockitoBean
    private ValidateApplicationUseCase validateApplicationUseCase;

    @MockitoBean
    private RejectApplicationUseCase rejectApplicationUseCase;

    @MockitoBean
    private SendContractUseCase sendContractUseCase;

    @MockitoBean
    private CancelContractUseCase cancelContractUseCase;

    @MockitoBean
    private IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;

    @MockitoBean
    private CancelInsurancePolicyUseCase cancelInsurancePolicyUseCase;

    @MockitoBean
    private SendSignatureReminderUseCase sendSignatureReminderUseCase;

    @MockitoBean
    private SendCancellationConfirmationUseCase sendCancellationConfirmationUseCase;

    @MockitoBean
    private RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    @MockitoBean
    private BookCancellationCostsUseCase bookCancellationCostsUseCase;

    @MockitoBean
    private OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    private ActivateLeasingUseCase activateLeasingUseCase;

    @BeforeEach
    public void setUp() {
        init(processEngine);
        given(orderBikeUseCase.orderBike(any()))
            .willReturn(new OrderBikeUseCase.Result(new OrderId("ORDER-1"), true));
    }

    @Test
    @DisplayName("happy path - contract signed, bike available, leasing becomes active")
    public void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // validate -> DMN -> conclude-contract sub-process parks on the signature wait state
        continueToNextWaitState(processEngine);

        process.correlateContractSigned(id); // forks into insurance + bike order, joins -> handover wait state
        continueToNextWaitState(processEngine);

        process.correlateHandoverReported(id); // -> withdrawal-period timer
        continueToNextWaitState(processEngine);

        fireTimer(processEngine, Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED);
        continueToNextWaitState(processEngine);

        assertThat(instance)
            .isEnded()
            .hasPassedInOrder(
                Elements.SERVICE_TASK_VALIDATE_APPLICATION.getValue(),
                Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.getValue(),
                Elements.SERVICE_TASK_SEND_CONTRACT.getValue(),
                Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY.getValue(),
                Elements.EVENT_HANDOVER_REPORTED.getValue(),
                Elements.SERVICE_TASK_ACTIVATE_LEASING.getValue(),
                Elements.END_EVENT_LEASING_ACTIVE.getValue())
            .hasNotPassed(
                Elements.END_EVENT_APPLICATION_REJECTED.getValue(),
                Elements.END_EVENT_APPLICATION_CANCELLED.getValue(),
                Elements.END_EVENT_CONTRACT_CANCELLED.getValue());

        then(sendContractUseCase).should(times(1)).sendContract(id);
        then(issueInsurancePolicyUseCase).should(times(1)).issuePolicy(id);
        then(activateLeasingUseCase).should(times(1)).activate(id);
    }

    @Test
    @DisplayName("escalation - contract not signed in time is escalated and rejected")
    public void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        continueToNextWaitState(processEngine); // parks on the signature wait state

        fireTimer(processEngine, Elements.EVENT_SIGNATURE_DEADLINE); // deadline -> escalation -> rejection
        continueToNextWaitState(processEngine);

        assertThat(instance)
            .isEnded()
            .hasPassed(
                Elements.EVENT_SIGNATURE_DEADLINE.getValue(),
                Elements.EVENT_CONTRACT_NOT_SIGNED.getValue(),
                Elements.SERVICE_TASK_SEND_REJECTION.getValue(),
                Elements.END_EVENT_APPLICATION_REJECTED.getValue())
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.getValue());

        then(rejectApplicationUseCase).should(times(1)).reject(id);
    }

    @Test
    @DisplayName("not solvent - the DMN routes the application straight to rejection")
    public void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        ApplicationId id = submit(15, 3500.0);

        continueToNextWaitState(processEngine); // validate -> DMN -> not solvent -> rejection -> end

        then(rejectApplicationUseCase).should(times(1)).reject(id);
        then(sendContractUseCase).should(never()).sendContract(any());
    }

    @Test
    @DisplayName("abort - withdrawing the application compensates the completed steps")
    public void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        given(requestOrderCancellationUseCase.requestCancellation(any())).willReturn(true);

        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // drive up to the handover wait state (contract signed, bike ordered, insured)
        continueToNextWaitState(processEngine);
        process.correlateContractSigned(id);
        continueToNextWaitState(processEngine);

        // Withdrawing triggers compensation; its handlers run in an engine-defined order, so drive the
        // continuations generically until the cancelBikeOrder sub-process parks on its user task.
        process.correlateApplicationWithdrawn(id);
        continueToNextWaitState(processEngine);

        Task task =
            taskService
                .createTaskQuery()
                .taskDefinitionKey(CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.getValue())
                .singleResult();
        taskService.complete(task.getId(), Map.of("returnClarified", true));
        continueToNextWaitState(processEngine);

        assertThat(instance)
            .isEnded()
            .hasPassed(
                Elements.SERVICE_TASK_CANCEL_CONTRACT.getValue(),
                Elements.SERVICE_TASK_CANCEL_POLICY.getValue(),
                Elements.CALL_ACTIVITY_CANCEL_BIKE_ORDER.getValue(),
                Elements.SERVICE_TASK_SEND_CANCELLATION_CONFIRMATION.getValue(),
                Elements.END_EVENT_APPLICATION_CANCELLED.getValue())
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.getValue());

        // External-task delivery is at-least-once: while compensation runs several handlers in
        // parallel, an async continuation firing on the test thread can force an optimistic-locking
        // retry of a handler that already ran, so a worker's use case may be invoked more than once
        // (workers are expected to be idempotent). The process still ends correctly, as asserted above.
        then(cancelContractUseCase).should(atLeast(1)).cancelContract(id);
        then(cancelInsurancePolicyUseCase).should(atLeast(1)).cancelPolicy(id);
        then(sendCancellationConfirmationUseCase).should(atLeast(1)).sendCancellationConfirmation(id);
    }

    @Test
    @DisplayName("bike unavailable - clarifying an alternative re-orders and leasing becomes active")
    public void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        // the first order finds the requested bike unavailable, the re-order after the alternative succeeds
        given(orderBikeUseCase.orderBike(any()))
            .willReturn(
                new OrderBikeUseCase.Result(null, false),
                new OrderBikeUseCase.Result(new OrderId("ORDER-2"), true));

        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        continueToNextWaitState(processEngine); // parks on the signature wait state
        process.correlateContractSigned(id);
        continueToNextWaitState(processEngine); // fork -> order finds bike unavailable -> parks on clarify-alternative

        // the alternative is clarified from the outside — the "external" completion of the user task
        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        continueToNextWaitState(processEngine); // re-order succeeds -> parallel join -> handover wait state

        process.correlateHandoverReported(id);
        continueToNextWaitState(processEngine);
        fireTimer(processEngine, Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED);
        continueToNextWaitState(processEngine);

        assertThat(instance)
            .isEnded()
            .hasPassed(
                Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue(),
                Elements.SERVICE_TASK_ORDER_BIKE.getValue(),
                Elements.END_EVENT_LEASING_ACTIVE.getValue())
            .hasNotPassed(
                Elements.END_EVENT_CONTRACT_CANCELLED.getValue(),
                Elements.END_EVENT_APPLICATION_REJECTED.getValue());

        then(orderBikeUseCase).should(times(2)).orderBike(id);
    }

    private ApplicationId submit(int age, double income) {
        return submit(age, income, "BIKE-TEST");
    }

    private ApplicationId submit(int age, double income, String bikeId) {
        LeasingApplication application =
            new LeasingApplication(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                age,
                income,
                new BikeId(bikeId),
                LeasingStatus.RECEIVED,
                LocalDateTime.now(),
                null,
                null);
        process.submitRequest(application);
        return application.id();
    }
}
