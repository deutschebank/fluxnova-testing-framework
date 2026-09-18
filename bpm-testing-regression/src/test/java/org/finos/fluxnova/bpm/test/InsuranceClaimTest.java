package org.finos.fluxnova.bpm.test;

import org.finos.fluxnova.bpm.test.helpers.DelegateHelpers;
import org.finos.fluxnova.bpm.test.mockito.FluxnovaMockito;
import org.finos.fluxnova.bpm.engine.delegate.BpmnError;
import org.finos.fluxnova.bpm.engine.repository.Deployment;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstantiationBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.finos.fluxnova.bpm.test.helpers.FlowHelpers.executeJob;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsuranceClaimTest extends ProcessTestExtension {

    @Autowired
    private DelegateMocks delegateMocks;

    @BeforeAll
    void before() {
        setup("insurance_claim_process.bpmn");
    }

    @AfterAll
    void after() {
        teardown();
    }

    @Test
    void standardApproval() {
        DelegateHelpers.mockDelegate(delegateMocks.claimValidationDelegate())
                .setVariable("claimStatus", "VALIDATED")
                .mock();

        Deployment standardApproval = FluxnovaMockito.registerCallActivityMock("StandardApprovalProcess")
                .onExecutionAddVariable("reviewResult", "approved")
                .deploy(repositoryService());
        fluxnova.manageDeployment(standardApproval);

        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey("Insurance_Claim_Process")
                .setVariable("claimComplete", true)
                .setVariable("claimAmount", 5000)
                .execute();

        executeJob(1, instance);
        assertThat(instance).isWaitingAt("Task_EvaluateClaim");
        complete(task(), withVariables("claimAmount", 5000L));

        assertThat(instance).hasPassed("CallActivity_StandardApproval");
        assertThat(instance).variables().containsEntry("claimStatus", "VALIDATED");
        assertThat(instance).variables().containsEntry("reviewResult", "approved");
        assertThat(instance).isWaitingAt("Task_ProcessPayment");
        complete(task());

        assertThat(instance).isStarted().isEnded().hasPassed("EndEvent_ClaimCompleted");
    }

    @Test
    void managerApproval() {
        DelegateHelpers.mockDelegate(delegateMocks.claimValidationDelegate())
                .setVariable("claimStatus", "VALIDATED")
                .mock();

        Deployment managerApproval = FluxnovaMockito.registerCallActivityMock("ManagerApprovalProcess")
                .onExecutionAddVariable("reviewResult", "manager_approved")
                .deploy(repositoryService());
        fluxnova.manageDeployment(managerApproval);

        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey("Insurance_Claim_Process")
                .setVariable("claimComplete", true)
                .setVariable("claimAmount", 50000)
                .execute();

        executeJob(1, instance);
        assertThat(instance).isWaitingAt("Task_EvaluateClaim");
        complete(task(), withVariables("claimAmount", 50000L));

        assertThat(instance).hasPassed("CallActivity_ManagerApproval");
        assertThat(instance).variables().containsEntry("claimStatus", "VALIDATED");
        assertThat(instance).variables().containsEntry("reviewResult", "manager_approved");
        assertThat(instance).isWaitingAt("Task_ProcessPayment");
        complete(task());

        assertThat(instance).isStarted().isEnded().hasPassed("EndEvent_ClaimCompleted");
    }

    @Test
    void defaultRoutesToRequestAdditionalInfo() {
        DelegateHelpers.mockDelegate(delegateMocks.claimValidationDelegate())
                .mock();

        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey("Insurance_Claim_Process")
                .execute();

        executeJob(1, instance);
        assertThat(instance).isWaitingAt("Task_RequestAdditionalInformation");
        assertThat(instance).variables().containsEntry("claimStatus", "RECEIVED");
        assertThat(instance).variables().containsEntry("claimComplete", false);
        assertThat(instance).variables().containsEntry("approvedClaim", false);
        assertThat(instance).variables().containsEntry("claimAmount", 0);
        assertThat(instance).variables().containsEntry("approvalThreshold", 10000);
    }

    @Test
    void errorPathTest() {
        DelegateHelpers.mockDelegate(delegateMocks.claimValidationDelegate())
                .setVariable("claimStatus", "VALIDATED")
                .mock();

        Deployment standardApproval = FluxnovaMockito.registerCallActivityMock("StandardApprovalProcess")
                .onExecutionAddVariable("reviewResult", "rejected")
                .deploy(repositoryService());
        fluxnova.manageDeployment(standardApproval);

        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey("Insurance_Claim_Process")
                .setVariable("claimComplete", true)
                .setVariable("claimAmount", 5000)
                .execute();

        executeJob(1, instance);
        assertThat(instance).isWaitingAt("Task_EvaluateClaim");
        complete(task(), withVariables("claimAmount", 5000L));

        assertThat(instance).hasPassed("CallActivity_StandardApproval");
        assertThat(instance).variables().containsEntry("claimStatus", "VALIDATED");
        assertThat(instance).variables().containsEntry("reviewResult", "rejected");
        assertThat(instance).isWaitingAt("Task_NotifyRejection");
        complete(task());

        assertThat(instance).isStarted().isEnded().hasPassed("EndEvent_ClaimRejected");
    }

    @Test
    void testDelegateExceptionHandling() {
        DelegateHelpers
                .mockDelegate(delegateMocks.claimValidationDelegate())
                .throwsException(new RuntimeException("Validation Error"))
                .mock();
        ProcessInstantiationBuilder pi = runtimeService()
                .createProcessInstanceByKey("Insurance_Claim_Process");

        Exception exception = assertThrows(RuntimeException.class, pi::execute);

        assertInstanceOf(RuntimeException.class, exception);
        assertEquals("Validation Error", exception.getMessage());
    }

    @Test
    void testDelegateBpmnErrorHandling() {
        DelegateHelpers.mockDelegate(delegateMocks.claimValidationDelegate())
                .setVariablesToNull("claimStatus")
                .throwsException(new BpmnError("500"))
                .mock();

        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey("Insurance_Claim_Process")
                .execute();

        assertThat(instance).variables().containsEntry("claimComplete", false);
        assertThat(instance).variables().containsEntry("approvedClaim", false);
    }
}
