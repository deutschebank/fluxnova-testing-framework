package org.finos.fluxnova.bpm.test.mockito.task;

import org.finos.fluxnova.bpm.test.data.FluxnovaBpmData;
import org.finos.fluxnova.bpm.test.data.factory.VariableFactory;
import org.finos.fluxnova.bpm.test.data.reader.VariableReader;
import org.finos.fluxnova.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LockedExternalTaskFakeTest {

  private final VariableFactory<String> var1 = FluxnovaBpmData.stringVariable("var1");
  private final VariableFactory<String> var2 = FluxnovaBpmData.stringVariable("var2");

  @Test
  public void create_locked_task() {
    LockedExternalTaskFake fake = LockedExternalTaskFake.builder()
      .id("1")
      .activityId("foo")
      .variables(Variables.putValue("var2", "world"))
      .variable(var1, "hello")
      .build();

    assertThat(fake.getId()).isEqualTo("1");
    assertThat(fake.getActivityId()).isEqualTo("foo");

    final VariableReader reader = FluxnovaBpmData.reader(fake.getVariables());

    assertThat(reader.get(var1)).isEqualTo("hello");
    assertThat(reader.get(var2)).isEqualTo("world");

  }
}
