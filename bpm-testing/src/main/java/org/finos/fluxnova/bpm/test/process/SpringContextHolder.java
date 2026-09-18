package org.finos.fluxnova.bpm.test.process;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        SpringContextHolder.applicationContext = applicationContext;
    }


    public static org.finos.fluxnova.bpm.engine.ProcessEngine getProcessEngine() {
        ApplicationContext context = applicationContext;
        return context == null ? null : context.getBean(org.finos.fluxnova.bpm.engine.ProcessEngine.class);
    }
}

