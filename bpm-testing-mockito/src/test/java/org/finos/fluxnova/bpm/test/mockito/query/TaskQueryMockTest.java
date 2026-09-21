package org.finos.fluxnova.bpm.test.mockito.query;

import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.finos.fluxnova.bpm.engine.task.TaskQuery;
import org.finos.fluxnova.bpm.test.mockito.FluxnovaMockito;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

//import org.finos.fluxnova.bpm.test.mockito.QueryMocks1;

public class TaskQueryMockTest {

  private AutoCloseable mocks;

  @Mock
  private TaskService taskService;

  @Mock
  private Task singleResult;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void should_mock_query_and_return_singleResult() {
    final TaskQuery taskQuery = FluxnovaMockito.mockTaskQuery(taskService).singleResult(singleResult);
    assertThat(taskService.createTaskQuery().singleResult()).isEqualTo(singleResult);

    Mockito.verify(taskQuery).singleResult();
  }

  @Test
  public void singleResult_for_activityName() {
    FluxnovaMockito.mockTaskQuery(taskService).singleResult(singleResult);
    assertThat(taskService.createTaskQuery().taskDefinitionKey("").singleResult()).isEqualTo(singleResult);
  }

  @Test
  public void singleResult_for_everything() {
    final TaskQuery taskQuery = new TaskQueryMock().forService(taskService).singleResult(singleResult);
    // @formatter:off
    assertThat(
      taskService.createTaskQuery()
        .taskDefinitionKey("")
        .processInstanceBusinessKey("")
        .taskDefinitionKey("")
        .taskId("")
        .taskUnassigned()
        .processInstanceId("pid")
        .active()
        .activityInstanceIdIn("")
        .dueAfter(new Date())
        .dueBefore(new Date())
        .dueDate(new Date())
        .excludeSubtasks()
        .executionId("")
        .processDefinitionId("")
        .processDefinitionKey("")
        .asc()
        .desc()
        .singleResult()).isEqualTo(singleResult);
    // @formatter:on

    verify(taskQuery).processInstanceId("pid");
  }

  @Test
  public void count_on_taskQuery() {
    new TaskQueryMock().forService(taskService).count(5);

    assertThat(taskService.createTaskQuery().active().processDefinitionKey("foo").count()).isEqualTo(5);
  }
}
