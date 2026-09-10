package io.miragon.blueprint.adapter.outbound.operaton;

import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd;
import dev.bpmcrafters.processengineapi.correlation.Correlation;
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Drives the embedded Operaton engine through the process-engine-api. The application id is used as
 * both the process business key (so the {@code cancelBikeOrder} call activity keeps the same key) and the
 * correlation key (so later messages correlate to the right instance). The variable names come from
 * the typed process API generated from {@code bike-leasing.bpmn}.
 *
 * <p>The {@code clarifyAlternative} user task is looked up in the {@link UserTaskSupport} pool (fed by the adapter's
 * user-task delivery) and then completed via the {@link UserTaskCompletionApi} — the process-engine-api
 * has no server-side task query, so completion always goes through a resolved task id.
 */
@Component
public class LeasingProcessAdapter implements LeasingProcess {

    private static final int TASK_LOOKUP_ATTEMPTS = 50;
    private static final long TASK_LOOKUP_INTERVAL_MS = 200L;

    private final StartProcessApi startProcessApi;
    private final CorrelationApi correlationApi;
    private final UserTaskSupport userTaskSupport;
    private final UserTaskCompletionApi userTaskCompletionApi;

    public LeasingProcessAdapter(
        StartProcessApi startProcessApi,
        CorrelationApi correlationApi,
        UserTaskSupport userTaskSupport,
        UserTaskCompletionApi userTaskCompletionApi
    ) {
        this.startProcessApi = startProcessApi;
        this.correlationApi = correlationApi;
        this.userTaskSupport = userTaskSupport;
        this.userTaskCompletionApi = userTaskCompletionApi;
    }

    @Override
    public void submitRequest(LeasingApplication application) {
        var key = application.id().value().toString();
        var payload = new HashMap<String, Object>();
        payload.put(Variables.StartEventLeasingRequestReceived.APPLICATION_ID.getValue(), key);
        payload.put(Variables.StartEventLeasingRequestReceived.BIKE_ID.getValue(), application.bikeId().value());
        payload.put(Variables.StartEventLeasingRequestReceived.MONTHLY_NET_INCOME.getValue(),
            application.monthlyNetIncome());
        payload.put(Variables.StartEventLeasingRequestReceived.AGE.getValue(), application.age());
        // Preserve the engine business key and seed the global correlation variable.
        payload.put(CommonRestrictions.BUSINESS_KEY, key);
        payload.put(CommonRestrictions.CORRELATION_KEY, key);
        startProcessApi.startProcess(
            new StartProcessByMessageCmd(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(), payload)
        ).join();
    }

    @Override
    public void correlateContractSigned(ApplicationId id) {
        correlate(Messages.MIRAVELO_CONTRACT_SIGNED.getValue(), id);
    }

    @Override
    public void correlateHandoverReported(ApplicationId id) {
        correlate(Messages.MIRAVELO_HANDOVER_REPORTED.getValue(), id);
    }

    @Override
    public void correlateApplicationWithdrawn(ApplicationId id) {
        correlate(Messages.MIRAVELO_APPLICATION_WITHDRAWN.getValue(), id);
    }

    /**
     * Correlates {@code messageName} to the instance whose global {@code correlationKey} variable equals the id.
     * A no-longer-valid correlation (e.g. the token already left the wait state) surfaces the engine's
     * {@link org.operaton.bpm.engine.MismatchingMessageCorrelationException}, which the REST advice maps to a
     * 409 — so it is unwrapped from the {@link CompletionException} the async API wraps it in.
     */
    private void correlate(String messageName, ApplicationId id) {
        try {
            correlationApi.correlateMessage(
                new CorrelateMessageCmd(
                    messageName,
                    Map.of(),
                    Correlation.withKey(id.value().toString()),
                    CommonRestrictions.builder()
                        .withRestriction("useGlobalCorrelationKey", "true")
                        .build()
                )
            ).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
    }

    /**
     * Completes the {@code Clarify alternative with customer} user task via the engine client — the same
     * task a human could complete through its deployed Camunda Form in the Tasklist.
     */
    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, @Nullable BikeId bikeId) {
        var taskId = awaitClarifyAlternativeTaskId(id);
        var variables = new HashMap<String, Object>();
        variables.put(Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.getValue(), alternativeFound);
        // The re-order reads the same start-injected bike variable, so reuse its name.
        if (bikeId != null) {
            variables.put(Variables.StartEventLeasingRequestReceived.BIKE_ID.getValue(), bikeId.value());
        }
        userTaskCompletionApi.completeTask(new CompleteTaskCmd(taskId, variables)).join();
    }

    /**
     * Resolves the open {@code clarifyAlternative} task id for {@code id} from the {@link UserTaskSupport} pool,
     * waiting briefly for the adapter's user-task delivery to hand it over. Fails loudly if none appears.
     */
    private String awaitClarifyAlternativeTaskId(ApplicationId id) {
        var applicationId = id.value().toString();
        var applicationIdKey = Variables.StartEventLeasingRequestReceived.APPLICATION_ID.getValue();
        for (int attempt = 0; attempt < TASK_LOOKUP_ATTEMPTS; attempt++) {
            var task = userTaskSupport.getAllTasks().stream()
                .filter(it -> Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue()
                    .equals(it.getMeta().get(CommonRestrictions.ACTIVITY_ID)))
                .filter(it -> belongsToApplication(it, applicationIdKey, applicationId))
                .findFirst()
                .orElse(null);
            if (task != null) {
                return task.getTaskId();
            }
            sleep(TASK_LOOKUP_INTERVAL_MS);
        }
        throw new IllegalStateException(
            "No open '" + Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue() + "' task for application "
                + applicationId);
    }

    private boolean belongsToApplication(TaskInformation task, String applicationIdKey, String applicationId) {
        try {
            return applicationId.equals(userTaskSupport.getPayload(task.getTaskId()).get(applicationIdKey));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
