package io.miragon.blueprint.adapter.outbound.operaton;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstanceQuery;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.task.TaskQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class TaskInboxAdapterTest {

    private final TaskService taskService = mock(TaskService.class);
    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final TaskInboxAdapter underTest = new TaskInboxAdapter(taskService, runtimeService);

    private final String applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString();

    @Test
    @DisplayName("translates open clarify-alternative tasks into their application business keys")
    void translatesOpenClarifyAlternativeTasksIntoTheirApplicationBusinessKeys() {
        // given: one active clarify-alternative task on an instance keyed by the application id
        var task = mock(Task.class);
        given(task.getProcessInstanceId()).willReturn("proc-1");
        given(task.getCreateTime()).willReturn(new Date(0));
        var taskQuery = stubTaskQuery(List.of(task));
        stubProcessInstanceQuery("proc-1", applicationId);

        // when: the inbox is read
        var result = underTest.findOpenClarifications();

        // then: the open task surfaces as its application id, queried by the clarify-alternative key
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().applicationId()).isEqualTo(ApplicationId.of(applicationId));
        then(taskQuery).should().taskDefinitionKey(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue());
    }

    @Test
    @DisplayName("drops a task whose instance has no resolvable business key")
    void dropsATaskWhoseInstanceHasNoResolvableBusinessKey() {
        // given: an open task whose process instance is not returned by the lookup
        var task = mock(Task.class);
        given(task.getProcessInstanceId()).willReturn("proc-missing");
        given(task.getCreateTime()).willReturn(new Date(0));
        stubTaskQuery(List.of(task));
        stubProcessInstanceQuery("proc-other", applicationId);

        // when / then: the unresolved task is skipped
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    @Test
    @DisplayName("returns nothing and skips the instance query when no task is open")
    void returnsNothingAndSkipsTheInstanceQueryWhenNoTaskIsOpen() {
        // given: no open tasks
        stubTaskQuery(List.of());

        // when / then: the result is empty; the (empty) instance lookup returns an empty map
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    private TaskQuery stubTaskQuery(List<Task> returns) {
        var query = mock(TaskQuery.class);
        given(taskService.createTaskQuery()).willReturn(query);
        given(query.taskDefinitionKey(any())).willReturn(query);
        given(query.active()).willReturn(query);
        given(query.list()).willReturn(returns);
        return query;
    }

    private void stubProcessInstanceQuery(String id, String businessKey) {
        var instance = mock(ProcessInstance.class);
        given(instance.getId()).willReturn(id);
        given(instance.getBusinessKey()).willReturn(businessKey);
        var query = mock(ProcessInstanceQuery.class);
        given(runtimeService.createProcessInstanceQuery()).willReturn(query);
        given(query.processInstanceIds(any())).willReturn(query);
        given(query.list()).willReturn(List.of(instance));
    }
}
