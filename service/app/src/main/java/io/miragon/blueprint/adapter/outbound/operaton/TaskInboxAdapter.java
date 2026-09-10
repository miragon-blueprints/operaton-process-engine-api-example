package io.miragon.blueprint.adapter.outbound.operaton;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.task.Task;
import org.springframework.stereotype.Component;

/**
 * Reads the open {@code Clarify alternative with customer} tasks from the engine's task list and translates
 * them into the domain's business key (the application id). It never leaks an engine task id upward:
 * the inbox lists cases, and cases are resolved through the domain, correlated by id.
 *
 * <p>This is a read-only query at the engine boundary. The process-engine-api has no server-side task
 * query, so the inbox reads the embedded Operaton {@link TaskService} directly — the native interfaces that
 * remain available alongside the API (see {@code docs/execution-and-task-listeners.md}). Task <em>completion</em>
 * still goes through the process-engine-api in {@link LeasingProcessAdapter}.
 */
@Component
public class TaskInboxAdapter implements TaskInboxPort {

    private final TaskService taskService;
    private final RuntimeService runtimeService;

    public TaskInboxAdapter(TaskService taskService, RuntimeService runtimeService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
    }

    @Override
    public List<TaskInboxPort.OpenClarification> findOpenClarifications() {
        var tasks = findOpenTasks(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue());
        var businessKeys = businessKeysById(tasks.stream().map(Task::getProcessInstanceId).toList());
        var clarifications = new ArrayList<TaskInboxPort.OpenClarification>();
        for (var task : tasks) {
            var applicationId = businessKeys.get(task.getProcessInstanceId());
            if (applicationId == null) {
                continue;
            }
            clarifications.add(new TaskInboxPort.OpenClarification(
                ApplicationId.of(applicationId),
                task.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            ));
        }
        return clarifications;
    }

    /** All currently-active tasks of the given {@code taskDefinitionKey}, across every process instance. */
    private List<Task> findOpenTasks(String taskDefinitionKey) {
        return taskService.createTaskQuery()
            .taskDefinitionKey(taskDefinitionKey)
            .active()
            .list();
    }

    /**
     * Maps the given process-instance ids to their business keys in one query. Returns an empty map for
     * an empty input so callers don't issue a pointless query.
     */
    private Map<String, String> businessKeysById(Collection<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        var businessKeysById = new HashMap<String, String>();
        for (var instance : runtimeService.createProcessInstanceQuery()
            .processInstanceIds(new HashSet<>(processInstanceIds))
            .list()) {
            businessKeysById.put(instance.getId(), instance.getBusinessKey());
        }
        return businessKeysById;
    }
}
