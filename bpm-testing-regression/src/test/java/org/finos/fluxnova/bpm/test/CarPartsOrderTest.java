package org.finos.fluxnova.bpm.test;

import org.finos.fluxnova.bpm.test.helpers.DelegateHelpers;
import org.finos.fluxnova.bpm.test.mockito.FluxnovaMockito;
import org.finos.fluxnova.bpm.engine.delegate.BpmnError;
import org.finos.fluxnova.bpm.engine.repository.Deployment;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

class CarPartsOrderTest extends ProcessTestExtension {

    @Autowired
    DelegateMocks delegateMocks;

    @BeforeAll
    void beforeAll() {
        setup("CarParts_Order.bpmn");
    }

    @AfterAll
    void after() {
        teardown();
    }

    @Test
    void testCarPartsOrder_RegularCarPartOrder() {
        String testRegistration = "231D12345";
        DelegateHelpers.mockDelegate(delegateMocks.carPartsDelegate())
                .setVariable("carRegistration", testRegistration)
                .mock();

        Deployment orderRegularPartActivity = FluxnovaMockito.registerCallActivityMock("OrderRegularPartProcess")
                .onExecutionAddVariable("result", "success")
                .deploy(repositoryService());
        fluxnova.manageDeployment(orderRegularPartActivity);

        Deployment orderVintagePartActivity = FluxnovaMockito.registerCallActivityMock("OrderVintagePartProcess")
                .onExecutionAddVariable("result", "success")
                .deploy(repositoryService());
        fluxnova.manageDeployment(orderVintagePartActivity);

        stubFor(get(urlEqualTo("/car-registration/" + testRegistration)).willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
                        .withBodyFile("carMakeModelResponse.json")));

        ProcessInstance pi = runtimeService()
                .createProcessInstanceByKey("CarPartsOrderProcessKey")
                .setVariable("carAge", 3)
                .execute();

        assertThat(pi).isWaitingAt("Activity_1sosy45");
        complete(task(), withVariables("carRegistration", testRegistration, "searchUrl", "https://carparts.com", "isVintage", false));

        assertThat(pi).isStarted().isEnded().hasPassed("Regular_PartOrder_End");
    }

    @Test
    void testCarPartsOrder_VintageCarPartOrder() {
        String testRegistration = "ZV88899";
        DelegateHelpers.mockDelegate(delegateMocks.carPartsDelegate())
                .setVariable("carRegistration", testRegistration)
                .mock();

        Deployment orderRegularPartActivity = FluxnovaMockito.registerCallActivityMock("OrderRegularPartProcess")
                .onExecutionAddVariable("result", "success")
                .deploy(repositoryService());
        fluxnova.manageDeployment(orderRegularPartActivity);

        Deployment orderVintagePartActivity = FluxnovaMockito.registerCallActivityMock("OrderVintagePartProcess")
                .onExecutionAddVariable("result", "success")
                .deploy(repositoryService());
        fluxnova.manageDeployment(orderVintagePartActivity);

        stubFor(get(urlEqualTo("/car-registration/" + testRegistration)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("carMakeModelVintageResponse.json")));

        ProcessInstance pi = runtimeService()
                .createProcessInstanceByKey("CarPartsOrderProcessKey")
                .setVariable("carAge", 31)
                .execute();

        assertThat(pi).isWaitingAt("Activity_1sosy45");
        complete(task(), withVariables("carRegistration", testRegistration, "searchUrl", "https://vintagecarparts.com", "isVintage", true));

        assertThat(pi).isStarted().isEnded().hasPassed("Vintage_PartOrder_End");
    }

    @Test
    void testCarPartsOrder_CarLookupApiFail() {
        String testRegistration = "ABC123";
        DelegateHelpers.mockDelegate(delegateMocks.carPartsDelegate())
                .setVariable("carRegistration", testRegistration)
                .mock();

        stubFor(get(urlEqualTo("/car-registration/" + testRegistration)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("")));

        ProcessInstance pi = runtimeService()
                .createProcessInstanceByKey("CarPartsOrderProcessKey")
                .setVariable("carAge", 3)
                .execute();

        assertThat(pi).isWaitingAt("Activity_1sosy45");
        complete(task(), withVariables("carRegistration", testRegistration, "searchUrl", "https://carparts.com", "isVintage", false));

        assertThat(pi).variables().containsEntry("result", "fail");
    }

    @Test
    void testCarPartsOrder_RegistrationNotProvided() {

        DelegateHelpers.mockDelegate(delegateMocks.carPartsDelegate())
                .setVariablesToNull("carRegistration")
                .throwsException(new BpmnError("500"))
                .mock();

        ProcessInstance pi = runtimeService()
                .createProcessInstanceByKey("CarPartsOrderProcessKey")
                .setVariable("searchUrl", "https://carparts.com")
                .setVariable("isVintage", false)
                .execute();

        assertThat(pi).isWaitingAt("Activity_1sosy45");
        complete(task(), withVariables("carRegistration", null));

        assertThat(pi).variables().containsEntry("result", "fail");

    }

}
