package org.finos.fluxnova.bpm.test;

import org.finos.fluxnova.bpm.test.helpers.FlowHelpers;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;

class FinanceParallelLoanReviewTest extends ProcessTestExtension {

    @BeforeAll void beforeAll() {
        setup("FinanceParallelLoanReview.bpmn");
    }

    @AfterAll void afterAll() {
        teardown();
    }

    @ParameterizedTest(name = "[{index}] payment_failures={0}, credit_limits_breached={1}, account_status={2}, risk_score={3} => {4}")
    @MethodSource("loanReviewScenarios")
    void testFinanceParallelLoanReview(
            int paymentFailures,
            String creditLimitsBreached,
            String accountStatus,
            int riskScore,
            String expectedLoanStatus
    ) {
        ProcessInstance pi = runtimeService()
                .createProcessInstanceByKey("FinanceLoanReviewProcess")
                .setVariable("payment_failures", paymentFailures)
                .setVariable("credit_limits_breached", creditLimitsBreached)
                .setVariable("account_status", accountStatus)
                .setVariable("risk_score", riskScore)
                .execute();

        FlowHelpers.advanceFlowThroughAllUntilAction(pi.getId());

        assertThat(pi).isStarted().isEnded();
        assertThat(pi).hasPassed("StartEvent_LoanReceived", "Gateway_SplitChecks", "Task_CreditCheck", "Task_RiskScore",
                        "Gateway_JoinChecks", "Task_FinalDecision", "EndEvent_ApprovedOrRejected");
        assertThat(pi).variables().containsEntry("LOAN_STATUS", expectedLoanStatus);
    }

    private static Stream<Arguments> loanReviewScenarios() {
        return Stream.of(
                Arguments.of(0, "false", "current",8, "APPROVED"),
                Arguments.of(2, "false", "current",8, "REJECTED"),
                Arguments.of(0, "false", "current",3, "REJECTED")
        );
    }
}