package org.finos.fluxnova.bpm.test.mockito.answer;

import org.finos.fluxnova.bpm.engine.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.exceptions.misusing.WrongTypeOfReturnValue;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public class FluentAnswerTest {

  @Mock
  private InvocationOnMock invocationOnMock;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void shouldReturnFluentAnserOrNull() {

    String string = "Bar";
    FluentBuilder superType = new FluentBuilder("superType");
    FluentBuilder superTypeInterface = new FluentBuilder("superInterface");

    // given
    FluentBuilder mock = FluentAnswer.createMock(FluentBuilder.class);

    // the fluent mock will not be returned on super type / interface or a different type
    // stub three methods
    when(mock.string()).thenReturn(string);
    when(mock.superType()).thenReturn(superType);
    when(mock.superTypeInterface()).thenReturn(superTypeInterface);


    // when -> then
    assertThat(mock.typeMatch()).isEqualTo(mock);
    assertThat(mock.typeMatch("")).isEqualTo(mock);
    assertThat(mock.asc()).isEqualTo(mock);
    assertThat(mock.string()).isEqualTo(string);
    assertThat(mock.superType()).isEqualTo(superType);
    assertThat(mock.superTypeInterface()).isEqualTo(superTypeInterface);
    // not stubbed method -> the result is not from the answer, but is null
    assertThat(mock.notStubbed()).isNull();

  }

  @Test
  public void shouldThrowClassCastExceptionUsingSubtypes() {
    FluentBuilder mock = FluentAnswer.createMock(FluentBuilder.class);

    assertThatThrownBy(() -> when(mock.subType()).thenReturn(new FluentBuilderExtension()))
      .isInstanceOf(WrongTypeOfReturnValue.class);
  }

  interface SomeAspect {
  }

  static class FluentBuilder implements SomeAspect, Query<FluentBuilder, Object> {
    private final String value;

    public FluentBuilder(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final FluentBuilder that = (FluentBuilder) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    // object is a supertype of all objects
    Object superType() {
      return this;
    }

    // supertype
    SomeAspect superTypeInterface() {
      return this;
    }


    // full match
    FluentBuilder typeMatch() {
      return this;
    }

    // full match
    FluentBuilder typeMatch(String value) {
      return this;
    }

    // subclass of current
    FluentBuilderExtension subType() {
      return new FluentBuilderExtension();
    }

    String notStubbed() {
      return "Zee";
    }


    // not in type hierarchy
    String string() {
      return "Hello";
    }

    @Override
    public FluentBuilder asc() {
      return null;
    }

    @Override
    public FluentBuilder desc() {
      return null;
    }

    @Override
    public long count() {
      return 0;
    }

    @Override
    public Object singleResult() {
      return null;
    }

    @Override
    public List<Object> list() {
      return null;
    }

    @Override
    public List<Object> unlimitedList() {
      return list();
    }

    @Override
    public List<Object> listPage(final int firstResult, final int maxResults) {
      return null;
    }
  }

  static class FluentBuilderExtension extends FluentBuilder {
    FluentBuilderExtension() {
      super("Extension");
    }
  }
}
