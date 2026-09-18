package org.finos.fluxnova.bpm.test;

import org.finos.fluxnova.bpm.test.process.RegressionProcessTestExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

/**
 * Shared base for regression tests so Spring Boot and the process coverage listener are configured once.
 */
@SpringBootTest
@TestExecutionListeners(
        listeners = org.finos.fluxnova.bpm.test.spring.platform.ProcessEngineCoverageTestExecutionListener.class,
        mergeMode = MergeMode.MERGE_WITH_DEFAULTS
)
public abstract class ProcessTestExtension extends RegressionProcessTestExtension {
}

