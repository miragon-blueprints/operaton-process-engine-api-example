package io.miragon.blueprint.adapter.outbound.operaton;

import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.Empty;
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd;
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi;
import dev.bpmcrafters.processengineapi.process.ProcessInformation;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class LeasingProcessAdapterTest {

    private final StartProcessApi startProcessApi = mock(StartProcessApi.class);
    private final CorrelationApi correlationApi = mock(CorrelationApi.class);
    private final UserTaskSupport userTaskSupport = mock(UserTaskSupport.class);
    private final UserTaskCompletionApi userTaskCompletionApi = mock(UserTaskCompletionApi.class);
    private final LeasingProcessAdapter underTest = new LeasingProcessAdapter(
        startProcessApi,
        correlationApi,
        userTaskSupport,
        userTaskCompletionApi
    );

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @Test
    @DisplayName("submitRequest starts the process by message with the application variables and keys")
    void submitRequestStartsTheProcessByMessageWithTheApplicationVariablesAndKeys() {

        // given: a leasing application and a captured start command
        var application = testLeasingApplication().id(id).build();
        given(startProcessApi.startProcess(any()))
            .willReturn(CompletableFuture.completedFuture(mock(ProcessInformation.class)));

        // when: the request is submitted
        underTest.submitRequest(application);

        // then: the leasing-request message starts the process with the DMN inputs, bike, business + correlation key
        ArgumentCaptor<StartProcessByMessageCmd> cmd = ArgumentCaptor.forClass(StartProcessByMessageCmd.class);
        then(startProcessApi).should().startProcess(cmd.capture());
        assertThat(cmd.getValue().getMessageName()).isEqualTo(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue());
        var payload = cmd.getValue().get();
        assertThat(payload.get("applicationId")).isEqualTo(id.value().toString());
        assertThat(payload.get("age")).isEqualTo(35);
        assertThat(payload.get("monthlyNetIncome")).isEqualTo(3500.0);
        assertThat(payload.get("bikeId")).isEqualTo("BIKE-900");
        assertThat(payload.get(CommonRestrictions.BUSINESS_KEY)).isEqualTo(id.value().toString());
        assertThat(payload.get(CommonRestrictions.CORRELATION_KEY)).isEqualTo(id.value().toString());
    }

    @Test
    @DisplayName("completeAlternativeClarification resolves the task by application id and completes it")
    void completeAlternativeClarificationResolvesTheTaskByApplicationIdAndCompletesIt() {

        // given: an open clarify-alternative task delivered to the user-task pool for this application
        var task = new TaskInformation(
            "task-1",
            Map.of(CommonRestrictions.ACTIVITY_ID, Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue())
        );
        given(userTaskSupport.getAllTasks()).willReturn(List.of(task));
        given(userTaskSupport.getPayload("task-1")).willReturn(Map.of("applicationId", id.value().toString()));
        given(userTaskCompletionApi.completeTask(any()))
            .willReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: an alternative bike is selected from the outside
        underTest.completeAlternativeClarification(id, true, new BikeId("BIKE-42"));

        // then: the resolved task is completed with the decision and the chosen bike
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        then(userTaskCompletionApi).should().completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-1");
        var payload = cmd.getValue().get();
        assertThat(payload.get("alternativeFound")).isEqualTo(true);
        assertThat(payload.get("bikeId")).isEqualTo("BIKE-42");
    }

    @Test
    @DisplayName("completeAlternativeClarification picks the clarify-alternative task of the given application")
    void completeAlternativeClarificationPicksTheClarifyAlternativeTaskOfTheGivenApplication() {

        // given: the pool also holds another activity's task, another application's task and one without payload
        var otherActivity = new TaskInformation(
            "task-other-activity",
            Map.of(CommonRestrictions.ACTIVITY_ID, "userTask_clarifyReturn")
        );
        var otherApplication = clarifyAlternativeTask("task-other-application");
        var withoutPayload = clarifyAlternativeTask("task-without-payload");
        var expected = clarifyAlternativeTask("task-expected");
        given(userTaskSupport.getAllTasks())
            .willReturn(List.of(otherActivity, otherApplication, withoutPayload, expected));
        given(userTaskSupport.getPayload("task-other-activity"))
            .willReturn(Map.of("applicationId", id.value().toString()));
        given(userTaskSupport.getPayload("task-other-application"))
            .willReturn(Map.of("applicationId", UUID.randomUUID().toString()));
        given(userTaskSupport.getPayload("task-without-payload"))
            .willThrow(new IllegalStateException("payload not delivered yet"));
        given(userTaskSupport.getPayload("task-expected")).willReturn(Map.of("applicationId", id.value().toString()));
        given(userTaskCompletionApi.completeTask(any()))
            .willReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: the clarification is completed without an alternative
        underTest.completeAlternativeClarification(id, false, null);

        // then: only the task of this application is completed, carrying the decision and no bike
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        then(userTaskCompletionApi).should().completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-expected");
        assertThat(cmd.getValue().get()).containsExactly(Map.entry("alternativeFound", false));
    }

    @Test
    @DisplayName("completeAlternativeClarification waits until the task is delivered to the pool")
    void completeAlternativeClarificationWaitsUntilTheTaskIsDeliveredToThePool() {

        // given: a pool that is still empty on the first lookup
        given(userTaskSupport.getAllTasks())
            .willReturn(List.of())
            .willReturn(List.of(clarifyAlternativeTask("task-late")));
        given(userTaskSupport.getPayload("task-late")).willReturn(Map.of("applicationId", id.value().toString()));
        given(userTaskCompletionApi.completeTask(any()))
            .willReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: the clarification is completed
        underTest.completeAlternativeClarification(id, true, new BikeId("BIKE-42"));

        // then: the pool is asked again and the late task is completed
        then(userTaskSupport).should(times(2)).getAllTasks();
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        then(userTaskCompletionApi).should().completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-late");
    }

    @Test
    @DisplayName("completeAlternativeClarification fails and stays interrupted when the wait for the task is interrupted")
    void completeAlternativeClarificationFailsAndStaysInterruptedWhenTheWaitForTheTaskIsInterrupted() {

        // given: no task in the pool and a thread that is asked to stop
        given(userTaskSupport.getAllTasks()).willReturn(List.of());
        Thread.currentThread().interrupt();

        // when / then: the lookup gives up at its first wait, keeps the interrupt flag and completes nothing
        try {
            assertThatThrownBy(() -> underTest.completeAlternativeClarification(id, true, new BikeId("BIKE-42")))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
        then(userTaskSupport).should(times(1)).getAllTasks();
        then(userTaskCompletionApi).should(never()).completeTask(any());
    }

    @Test
    @DisplayName("a failed correlation surfaces the engine's runtime exception instead of the async wrapper")
    void aFailedCorrelationSurfacesTheEnginesRuntimeExceptionInsteadOfTheAsyncWrapper() {

        // given: a correlation the engine refuses
        var refusal = new IllegalStateException("no matching subscription");
        given(correlationApi.correlateMessage(any())).willReturn(CompletableFuture.failedFuture(refusal));

        // when / then: the engine's exception itself reaches the caller
        assertThatThrownBy(() -> underTest.correlateContractSigned(id)).isSameAs(refusal);
    }

    @Test
    @DisplayName("a failed correlation surfaces an error instead of the async wrapper")
    void aFailedCorrelationSurfacesAnErrorInsteadOfTheAsyncWrapper() {

        // given: a correlation that dies with an error
        var error = new AssertionError("engine broke");
        given(correlationApi.correlateMessage(any())).willReturn(CompletableFuture.failedFuture(error));

        // when / then: the error itself reaches the caller
        assertThatThrownBy(() -> underTest.correlateContractSigned(id)).isSameAs(error);
    }

    @Test
    @DisplayName("a correlation failing with a checked exception keeps the async wrapper")
    void aCorrelationFailingWithACheckedExceptionKeepsTheAsyncWrapper() {

        // given: a correlation that fails with a checked exception
        var checked = new Exception("connection lost");
        given(correlationApi.correlateMessage(any())).willReturn(CompletableFuture.failedFuture(checked));

        // when / then: the wrapper is passed on with the checked exception as its cause
        assertThatThrownBy(() -> underTest.correlateContractSigned(id))
            .isInstanceOf(CompletionException.class)
            .hasCause(checked);
    }

    @Test
    @DisplayName("correlateContractSigned correlates the message by the global correlation key")
    void correlateContractSignedCorrelatesTheMessageByTheGlobalCorrelationKey() {
        assertCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.getValue(), () -> underTest.correlateContractSigned(id));
    }

    @Test
    @DisplayName("correlateHandoverReported correlates the message by the global correlation key")
    void correlateHandoverReportedCorrelatesTheMessageByTheGlobalCorrelationKey() {
        assertCorrelation(Messages.MIRAVELO_HANDOVER_REPORTED.getValue(), () -> underTest.correlateHandoverReported(id));
    }

    @Test
    @DisplayName("correlateApplicationWithdrawn correlates the message by the global correlation key")
    void correlateApplicationWithdrawnCorrelatesTheMessageByTheGlobalCorrelationKey() {
        assertCorrelation(
            Messages.MIRAVELO_APPLICATION_WITHDRAWN.getValue(),
            () -> underTest.correlateApplicationWithdrawn(id)
        );
    }

    private TaskInformation clarifyAlternativeTask(String taskId) {
        return new TaskInformation(
            taskId,
            Map.of(CommonRestrictions.ACTIVITY_ID, Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue())
        );
    }

    private void assertCorrelation(String expectedMessage, Runnable action) {

        // given: a captured correlate command
        given(correlationApi.correlateMessage(any())).willReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: the correlation is triggered
        action.run();

        // then: the expected message correlates to the instance whose global correlationKey equals the id
        ArgumentCaptor<CorrelateMessageCmd> cmd = ArgumentCaptor.forClass(CorrelateMessageCmd.class);
        then(correlationApi).should().correlateMessage(cmd.capture());
        assertThat(cmd.getValue().getMessageName()).isEqualTo(expectedMessage);
        assertThat(cmd.getValue().getCorrelation().get().getCorrelationKey()).isEqualTo(id.value().toString());
        assertThat(cmd.getValue().getRestrictions().get("useGlobalCorrelationKey")).isEqualTo("true");
    }
}
