package org.finos.fluxnova.bpm.test.mockito.answer;

import org.finos.fluxnova.bpm.engine.delegate.VariableScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import static org.mockito.Mockito.*;

public class AbstractAnswerTest {

  private AutoCloseable mocks;

  private AbstractAnswer<VariableScope> answer = spy(new AbstractAnswer<VariableScope>() {

    @Override
    protected void answer(final VariableScope parameter) {

    }
  });

  @Mock
  private VariableScope variableScope;

  @Mock
  private InvocationOnMock invocationOnMock;

  @BeforeEach
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void shouldDelegateToGenericAnswer() throws Throwable {
    when(invocationOnMock.getArguments()).thenReturn(new Object[] { variableScope });
    answer.answer(invocationOnMock);
    verify(answer).answer(variableScope);
  }

}
