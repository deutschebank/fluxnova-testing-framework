package org.finos.fluxnova.bpm.test.mockito;

import org.finos.fluxnova.bpm.engine.repository.DeploymentBuilder;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.test.junit5.ProcessEngineExtension;
import org.finos.fluxnova.bpm.test.mockito.process.CallActivityMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.finos.fluxnova.bpm.test.mockito.MostUsefulProcessEngineConfiguration.mostUsefulProcessEngineConfiguration;

public class CallActivityMockBindingDeploymentTest {

  public static final String KEY = "process_with_callActivity_binding_deployment";
  public static final String KEY_MOCK = "do_stuff_mock";

  @RegisterExtension
  static final ProcessEngineExtension fluxnova = ProcessEngineExtension.builder()
    .useProcessEngine(mostUsefulProcessEngineConfiguration().buildProcessEngine())
    .build();

  @BeforeEach
  public void setUp() {
    DeploymentBuilder deploymentBuilder = fluxnova.getRepositoryService().createDeployment();
    deploymentBuilder.addClasspathResource("process_with_callActivity_binding_deployment.bpmn");
    CallActivityMock mock = FluxnovaMockito.registerCallActivityMock(KEY_MOCK).onExecutionAddVariable("foo", "bar");
    mock.addToDeployment(deploymentBuilder);

    fluxnova.manageDeployment(deploymentBuilder.deploy());
  }

  @Test
  public void mock_runs_with_binding_deployment() {
    ProcessInstance processInstance = fluxnova.getRuntimeService().startProcessInstanceByKey(KEY);

    // instance waits in endEvent
    ProcessInstance found = fluxnova.getRuntimeService().createProcessInstanceQuery().processInstanceId(processInstance.getId())
      .activityIdIn("endEvent")
      .singleResult();
    assertThat(found).isNotNull();

    // subProcess set variable foo
    assertThat(fluxnova.getRuntimeService().getVariable(found.getProcessInstanceId(), "foo")).isEqualTo("bar");
  }
}
