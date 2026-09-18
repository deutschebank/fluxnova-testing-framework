package org.finos.fluxnova.bpm.test.mockito.verify;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.test.mockito.DelegateExpressions;
import org.finos.fluxnova.bpm.test.mockito.mock.FluentJavaDelegateMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;

public class MockitoVerificationTest {

  private static final String JAVA_DELEGATE = "javaDelegate";

  private final FluentJavaDelegateMock javaDelegate = DelegateExpressions.registerJavaDelegateMock(JAVA_DELEGATE);

  @Mock
  private DelegateExecution delegateExecution;

  private AutoCloseable mocks;

  @BeforeEach
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void shouldVerifyExecuteCalled() throws Exception {
    javaDelegate.execute(delegateExecution);

    DelegateExpressions.verifyJavaDelegateMock(JAVA_DELEGATE).executed();
  }

  @Test
  public void shouldVerifyExecuteCalledTwice() throws Exception {
    javaDelegate.execute(delegateExecution);
    javaDelegate.execute(delegateExecution);

    DelegateExpressions.verifyJavaDelegateMock(JAVA_DELEGATE).executed(times(2));
  }

  @Test
  public void shouldVerifyExecuteNotCalled() throws Exception {
    DelegateExpressions.verifyJavaDelegateMock(JAVA_DELEGATE).executedNever();
  }
}
