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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

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
