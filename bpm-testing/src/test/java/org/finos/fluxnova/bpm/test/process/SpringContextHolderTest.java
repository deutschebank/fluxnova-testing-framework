package org.finos.fluxnova.bpm.test.process;

import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringContextHolderTest {

    @AfterEach
    void resetApplicationContext() throws Exception {
        setApplicationContext(null);
    }

    @Test
    void getProcessEngine_returnsNullWhenContextIsNotSet() throws Exception {
        setApplicationContext(null);

        assertNull(SpringContextHolder.getProcessEngine());
    }

    @Test
    void getProcessEngine_returnsBeanFromApplicationContext() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ProcessEngine processEngine = mock(ProcessEngine.class);
        when(applicationContext.getBean(ProcessEngine.class)).thenReturn(processEngine);

        SpringContextHolder springContextHolder = new SpringContextHolder();
        springContextHolder.setApplicationContext(applicationContext);

        assertSame(processEngine, SpringContextHolder.getProcessEngine());
    }

    private static void setApplicationContext(ApplicationContext applicationContext) throws Exception {
        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, applicationContext);
    }
}

