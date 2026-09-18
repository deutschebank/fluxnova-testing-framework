package org.finos.fluxnova.bpm.test.mockito;

import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.test.Deployment;
import org.finos.fluxnova.bpm.engine.test.junit5.ProcessEngineExtension;
import org.finos.fluxnova.bpm.test.mockito.mock.FluentJavaDelegateMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.finos.fluxnova.bpm.engine.variable.Variables.createVariables;
import static org.finos.fluxnova.bpm.test.mockito.DelegateExpressions.*;
import static org.finos.fluxnova.bpm.test.mockito.MostUsefulProcessEngineConfiguration.mostUsefulProcessEngineConfiguration;

/**
 * @author Jan Galinski, Holisticon AG
 */
public class ManualMockProcessTest {

  @RegisterExtension
  static final ProcessEngineExtension processEngineRule = ProcessEngineExtension.builder()
    .useProcessEngine(mostUsefulProcessEngineConfiguration().buildProcessEngine())
    .build();

  @Test
  @Deployment(resources = "MockProcess.bpmn")
  public void deploy_and_run_process_with_manually_registered_mocks() {
    registerExecutionListenerMock("startProcess");
    final FluentJavaDelegateMock loadData = registerJavaDelegateMock("loadData");
    registerTaskListenerMock("verifyData").onExecutionSetVariables(createVariables().putValue("foo", "bar"));
    registerExecutionListenerMock("beforeLoadData");

    final ProcessInstance processInstance = processEngineRule.getRuntimeService().startProcessInstanceByKey("process_mock_dummy");

    assertThat(processEngineRule.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult()).isNotNull();

    assertThat((String) processEngineRule.getRuntimeService().getVariable(processInstance.getId(), "foo")).isEqualTo("bar");

    verifyJavaDelegateMock(loadData).executed();
    verifyExecutionListenerMock("startProcess").executed();
    verifyTaskListenerMock("verifyData").executed();

    // See if we can get the registered instances and modify their behavior.
    getJavaDelegateMock("loadData").onExecutionThrowBpmnError("error");
    getTaskListenerMock("verifyData").onExecutionThrowBpmnError("error");
    getExecutionListenerMock("startProcess").onExecutionThrowBpmnError("error");
  }
}
