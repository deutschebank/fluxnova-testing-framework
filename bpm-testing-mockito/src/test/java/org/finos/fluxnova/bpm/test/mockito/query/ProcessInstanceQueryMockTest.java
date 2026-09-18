package org.finos.fluxnova.bpm.test.mockito.query;

import org.assertj.core.util.Lists;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.test.mockito.FluxnovaMockito;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ProcessInstanceQueryMockTest {

  private AutoCloseable mocks;

  @Mock
  private RuntimeService runtimeServiceMock;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void should_not_throw_stubbing_exception_on_only_single_result() {

    // GIVEN
    FluxnovaMockito.mockProcessInstanceQuery(runtimeServiceMock)
                  .singleResult(mock(ProcessInstance.class));

    // WHEN
    final ProcessInstance result = runtimeServiceMock.createProcessInstanceQuery()
                                                     .processInstanceId("test")
                                                     .singleResult();
    // THEN
    assertThat(result).isNotNull();
  }

  @Test
  public void should_not_throw_stubbing_exception_on_only_list() {

    // GIVEN
    FluxnovaMockito.mockProcessInstanceQuery(runtimeServiceMock)
                  .list(Lists.newArrayList(mock(ProcessInstance.class)));

    // WHEN
    final List<ProcessInstance> result = runtimeServiceMock.createProcessInstanceQuery()
                                                           .processInstanceId("test")
                                                           .list();
    // THEN
    assertThat(result).isNotNull();
  }
}
