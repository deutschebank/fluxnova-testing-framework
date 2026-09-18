package org.finos.fluxnova.bpm.test.process;

import org.finos.fluxnova.bpm.test.TestException;
import org.finos.fluxnova.bpm.test.coverage.ProcessCoverage;
import org.finos.fluxnova.bpm.test.rules.MockConnectorRule;
import org.finos.fluxnova.bpm.test.scripting.ScriptTestUtils;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.ProcessEngines;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.repository.Deployment;
import org.finos.fluxnova.bpm.engine.repository.DeploymentBuilder;
import org.finos.fluxnova.bpm.engine.test.mock.Mocks;
import org.finos.fluxnova.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Objects;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RegressionProcessTestExtension {

    public static final FluxnovaFacade fluxnova = new FluxnovaFacade();

    public static final MockConnectorRule wireMockRule = new MockConnectorRule(8080);

    public static void setup(String bpmn, String... dependencies) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        try {
            RepositoryService repositoryService = getRepositoryService();
            DeploymentBuilder deploymentBuilder = repositoryService.createDeployment();
            deployModel(bpmn, deploymentBuilder, caller);
            for (String dependency : dependencies) {
                deploy(dependency, deploymentBuilder);
            }
            deploymentBuilder.deploy();
            wireMockRule.start();
        } catch (Exception e) {
            throw new TestException("Error deploying resource", e);
        }
    }

    public static void teardown() {
        try {
            wireMockRule.stop();
            Mocks.reset();
            RepositoryService repositoryService = getRepositoryService();
            for (Deployment deployment : repositoryService.createDeploymentQuery().list()) {
                repositoryService.deleteDeployment(deployment.getId(), true);
            }
            ProcessEngine processEngine = SpringContextHolder.getProcessEngine();
            if (processEngine != null) {
                ProcessEngines.unregister(processEngine);
            }
        } catch (Exception e) {
            throw new TestException("Error tearing down resource", e);
        }
    }

    private static RepositoryService getRepositoryService() {
        ProcessEngine processEngine = SpringContextHolder.getProcessEngine();
        if (processEngine != null) {
            ProcessEngines.registerProcessEngine(processEngine);
            return processEngine.getRepositoryService();
        }
        return org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.repositoryService();
    }

    private static void deployModel(String fileName, DeploymentBuilder deployment, Class<?> caller) throws IOException {
        Resource bpmn = deploy(fileName, deployment);
        BpmnModelInstance bpmnModelInstance = ScriptTestUtils.getBpmnModelInstance(bpmn.getInputStream().readAllBytes());
        String processDefinitionKey = ScriptTestUtils.getProcessDefinitionKey(bpmnModelInstance);
        ProcessCoverage.register(processDefinitionKey, caller);
    }

    private static Resource deploy(String fileName, DeploymentBuilder deployment) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:" + fileName);
        deployment.addInputStream(resource.getFilename(), resource.getInputStream());
        return resource;
    }

    public static class FluxnovaFacade {
        @SuppressWarnings("unused")
        public void manageDeployment(Deployment deployment) {
            Objects.requireNonNull(deployment.getId());
            // Deployment lifecycle is handled centrally in teardown(); this preserves the test API.
        }
    }
}
