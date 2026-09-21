package org.finos.fluxnova.bpm.test.process;

import org.finos.fluxnova.bpm.test.TestException;
import org.finos.fluxnova.bpm.test.coverage.ProcessCoverage;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.ProcessEngines;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.finos.fluxnova.bpm.engine.repository.Deployment;
import org.finos.fluxnova.bpm.engine.repository.DeploymentBuilder;
import org.finos.fluxnova.bpm.engine.repository.DeploymentQuery;
import org.junit.jupiter.api.AfterEach;
import org.finos.fluxnova.bpm.engine.test.assertions.ProcessEngineTests;
import org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessTestExtensionTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private DeploymentQuery deploymentQuery;

    @Mock
    private DeploymentBuilder deploymentBuilder;

    @Mock
    private Deployment deployment;

    @AfterEach
    void resetSpringContextHolder() throws Exception {
        setSpringApplicationContext(null);
    }

    @Test
    void teardown_shouldRedeployModels() {
        try (MockedStatic<BpmnAwareTests> processEngineMockedStatic = Mockito.mockStatic(BpmnAwareTests.class)) {
            processEngineMockedStatic.when(ProcessEngineTests::repositoryService).thenReturn(repositoryService);
            doReturn(deploymentQuery).when(repositoryService).createDeploymentQuery();
            List<Deployment> deployments = buildDeployments();
            doReturn(deployments).when(deploymentQuery).list();
            RegressionProcessTestExtension.teardown();
            verify(repositoryService, times(1)).deleteDeployment("1", true);
            verify(repositoryService, times(1)).deleteDeployment("2", true);
        }
    }

    @Test
    void setup_shouldDeployModel() {
        try (MockedStatic<BpmnAwareTests> processEngineMockedStatic = Mockito.mockStatic(BpmnAwareTests.class);
             MockedStatic<ProcessCoverage> processCoverageMockedStatic = Mockito.mockStatic(ProcessCoverage.class)) {
            processEngineMockedStatic.when(ProcessEngineTests::repositoryService).thenReturn(repositoryService);
            doReturn(deploymentBuilder).when(repositoryService).createDeployment();
            doReturn(deploymentBuilder).when(deploymentBuilder).addInputStream(any(), any());
            doReturn(deployment).when(deploymentBuilder).deploy();
            RegressionProcessTestExtension.setup("unit/script-task.bpmn");
            processCoverageMockedStatic.verify(() -> ProcessCoverage.register("Process_1k7woqc", ProcessTestExtensionTest.class), times(1));
            verify(deploymentBuilder, times(1)).addInputStream(eq("script-task.bpmn"), any());
        }
    }

    @Test
    void setup_shouldDeployModelAndDependencies() {
        try (MockedStatic<BpmnAwareTests> processEngineMockedStatic = Mockito.mockStatic(BpmnAwareTests.class);
             MockedStatic<ProcessCoverage> processCoverageMockedStatic = Mockito.mockStatic(ProcessCoverage.class)) {
            processEngineMockedStatic.when(BpmnAwareTests::repositoryService).thenReturn(repositoryService);
            doReturn(deploymentBuilder).when(repositoryService).createDeployment();
            doReturn(deploymentBuilder).when(deploymentBuilder).addInputStream(any(), any());
            doReturn(deployment).when(deploymentBuilder).deploy();
            RegressionProcessTestExtension.setup("unit/script-task.bpmn", "unit/hello-world.js", "unit/hello-world.groovy");
            processCoverageMockedStatic.verify(() -> ProcessCoverage.register("Process_1k7woqc", ProcessTestExtensionTest.class), times(1));
            verify(deploymentBuilder, times(1)).addInputStream(eq("script-task.bpmn"), any());
            verify(deploymentBuilder, times(1)).addInputStream(eq("hello-world.groovy"), any());
            verify(deploymentBuilder, times(1)).addInputStream(eq("hello-world.js"), any());

        }
    }

    @Test
    void setup_throwsDAPTestExceptionOnError() {
        try (MockedStatic<BpmnAwareTests> processEngineMockedStatic = Mockito.mockStatic(BpmnAwareTests.class)) {
            processEngineMockedStatic.when(ProcessEngineTests::repositoryService).thenReturn(repositoryService);
            doReturn(deploymentBuilder).when(repositoryService).createDeployment();
            doReturn(deploymentBuilder).when(deploymentBuilder).addInputStream(any(), any());
            TestException exception = assertThrows(TestException.class, () -> {
                RegressionProcessTestExtension.setup("unit/hello-world.js");
            });
            assertEquals("Error deploying resource", exception.getMessage());

        }
    }

    @Test
    void setup_shouldUseSpringManagedProcessEngineWhenAvailable() throws Exception {
        ProcessEngine processEngine = mock(ProcessEngine.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);

        when(applicationContext.getBean(ProcessEngine.class)).thenReturn(processEngine);
        when(processEngine.getRepositoryService()).thenReturn(repositoryService);
        doReturn(deploymentBuilder).when(repositoryService).createDeployment();
        doReturn(deploymentBuilder).when(deploymentBuilder).addInputStream(any(), any());
        doReturn(deployment).when(deploymentBuilder).deploy();
        setSpringApplicationContext(applicationContext);

        try (MockedStatic<ProcessCoverage> processCoverageMockedStatic = Mockito.mockStatic(ProcessCoverage.class);
             MockedStatic<ProcessEngines> processEnginesMockedStatic = Mockito.mockStatic(ProcessEngines.class)) {
            RegressionProcessTestExtension.setup("unit/script-task.bpmn");

            processEnginesMockedStatic.verify(() -> ProcessEngines.registerProcessEngine(processEngine), times(1));
            processCoverageMockedStatic.verify(() -> ProcessCoverage.register("Process_1k7woqc", ProcessTestExtensionTest.class), times(1));
            verify(repositoryService, times(1)).createDeployment();
        }
    }

    @Test
    void teardown_shouldUseSpringManagedProcessEngineWhenAvailable() throws Exception {
        ProcessEngine processEngine = mock(ProcessEngine.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);

        when(applicationContext.getBean(ProcessEngine.class)).thenReturn(processEngine);
        when(processEngine.getRepositoryService()).thenReturn(repositoryService);
        doReturn(deploymentQuery).when(repositoryService).createDeploymentQuery();
        doReturn(buildDeployments()).when(deploymentQuery).list();
        setSpringApplicationContext(applicationContext);

        try (MockedStatic<ProcessEngines> processEnginesMockedStatic = Mockito.mockStatic(ProcessEngines.class)) {
            RegressionProcessTestExtension.teardown();

            processEnginesMockedStatic.verify(() -> ProcessEngines.registerProcessEngine(processEngine), times(1));
            processEnginesMockedStatic.verify(() -> ProcessEngines.unregister(processEngine), times(1));
            verify(repositoryService, times(1)).deleteDeployment("1", true);
            verify(repositoryService, times(1)).deleteDeployment("2", true);
        }
    }

    @Test
    void fluxnovaFacade_manageDeployment_acceptsManagedDeployment() {
        when(deployment.getId()).thenReturn("managed-deployment");

        assertDoesNotThrow(() -> RegressionProcessTestExtension.fluxnova.manageDeployment(deployment));
    }

    private List<Deployment> buildDeployments() {
        List<Deployment> deployments = new ArrayList<>();
        DeploymentEntity firstDeployment = new DeploymentEntity();
        firstDeployment.setId("1");
        deployments.add(firstDeployment);
        DeploymentEntity secondDeployment = new DeploymentEntity();
        secondDeployment.setId("2");
        deployments.add(secondDeployment);
        return deployments;
    }

    private static void setSpringApplicationContext(ApplicationContext applicationContext) throws Exception {
        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, applicationContext);
    }
}
