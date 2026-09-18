package org.finos.fluxnova.bpm.test;

import org.finos.fluxnova.bpm.test.helpers.DelegateHelpers;
import org.finos.fluxnova.bpm.engine.delegate.BpmnError;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.util.*;

import static org.finos.fluxnova.bpm.test.helpers.FlowHelpers.advanceFlowUntilAction;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountOpeningTest extends ProcessTestExtension {

    @Autowired
    DelegateMocks delegateMocks;

    @BeforeAll
    void beforeAll() {
        setup("AccountOpening-InvestmentAccount.bpmn");
    }

    @AfterAll
    void after() {
        teardown();
    }

    @Test
    void testInputsFormFields() throws Exception {
        Document document = loadBpmnDocument();
        Map<String, String> customerFormFields = getFormFieldTypes(document, "Task_CaptureCustomerData");

        assertEquals(12, customerFormFields.size());
        assertEquals("string", customerFormFields.get("customerName"));
        assertEquals("string", customerFormFields.get("customerEmail"));
        assertEquals("string", customerFormFields.get("customerPhone"));
        assertEquals("string", customerFormFields.get("customerAddress"));
        assertEquals("string", customerFormFields.get("taxId"));
        assertEquals("date", customerFormFields.get("dateOfBirth"));
        assertEquals("string", customerFormFields.get("personalInvestments"));
        assertEquals("string", customerFormFields.get("iraAccounts"));
        assertEquals("string", customerFormFields.get("retirementAccounts"));
        assertEquals("long", customerFormFields.get("totalAssetValue"));
        assertEquals("string", customerFormFields.get("investmentGoals"));
        assertEquals("enum", customerFormFields.get("riskTolerance"));

        List<String> riskToleranceOptions = getEnumValueIds(document, "Task_CaptureCustomerData", "riskTolerance");
        assertEquals(List.of("conservative", "moderate", "aggressive"), riskToleranceOptions);
    }

    @Test
    void testInputsFormFieldTypes() throws Exception {
        Document document = loadBpmnDocument();

        Map<String, String> supportReviewFields = getFormFieldTypes(document, "Task_SupportReview");
        assertEquals(4, supportReviewFields.size());
        assertEquals("boolean", supportReviewFields.get("dataComplete"));
        assertEquals("string", supportReviewFields.get("missingDataDescription"));
        assertEquals("string", supportReviewFields.get("supportComments"));
        assertEquals("boolean", supportReviewFields.get("supportApproval"));

        Map<String, String> managerApprovalFields = getFormFieldTypes(document, "Task_ManagerApproval");
        assertEquals(3, managerApprovalFields.size());
        assertEquals("boolean", managerApprovalFields.get("managerApproval"));
        assertEquals("string", managerApprovalFields.get("managerComments"));
        assertEquals("string", managerApprovalFields.get("rejectionReason"));

        assertFalse(managerApprovalFields.containsKey("supportApproval"));
    }

    @Test
    void standardAccountPathReachesOpenAccount() {

        DelegateHelpers.mockDelegate(delegateMocks.sendEmailDelegate())
                .setVariable("emailResult", "success")
                .mock();
        DelegateHelpers.mockDelegate(delegateMocks.sendSMSDelegate())
                .setVariable("smsResult", "success")
                .mock();

        stubFor(post(urlEqualTo("/create")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("accountCreateResponse.json")));

        ProcessInstance instance = runtimeService().createProcessInstanceByKey("Customer_Onboarding_Process")
                .setVariable("customerId", "test-customer-1")
                .setVariable("accounts_url", "http://localhost:8080")
                .setVariable("apiToken", "test-token")
                .execute();

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");

        complete(task(), withVariables(
                "customerName", "Jane Doe",
                "customerEmail", "jane.doe@example.com",
                "customerPhone", "5551234567",
                "customerAddress", "100 Main St",
                "taxId", "123-45-6789",
                "dateOfBirth", "1990-01-01",
                "personalInvestments", "Index Funds",
                "iraAccounts", "Traditional IRA",
                "retirementAccounts", "401k",
                "totalAssetValue", 750000L,
                "investmentGoals", "Long-term growth",
                "riskTolerance", "moderate"
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_SupportReview");

        complete(task(), withVariables(
                "dataComplete", true,
                "supportApproval", true,
                "supportComments", "Validated",
                "missingDataDescription", ""
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).hasPassed("Task_CheckAssetValue");
        assertThat(instance).variables().containsEntry("accountType", "STANDARD");
    }

    @Test
    void premiumAccountPathReachesManagerApproval() {
        DelegateHelpers.mockDelegate(delegateMocks.sendEmailDelegate())
                .setVariable("emailResult", "success")
                .mock();
        DelegateHelpers.mockDelegate(delegateMocks.sendSMSDelegate())
                .setVariable("smsResult", "success")
                .mock();

        stubFor(post(urlEqualTo("/create")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("accountCreateResponse.json")));

        ProcessInstance instance = runtimeService().createProcessInstanceByKey("Customer_Onboarding_Process")
                .setVariable("customerId", "test-customer-1")
                .setVariable("accounts_url", "http://localhost:8080")
                .setVariable("apiToken", "test-token")
                .execute();

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");

        complete(task(), withVariables(
                "customerName", "Jane Doe",
                "customerEmail", "jane.doe@example.com",
                "customerPhone", "5551234567",
                "customerAddress", "100 Main St",
                "taxId", "123-45-6789",
                "dateOfBirth", "1990-01-01",
                "personalInvestments", "Index Funds",
                "iraAccounts", "Traditional IRA",
                "retirementAccounts", "401k",
                "totalAssetValue", 1500000L,
                "investmentGoals", "Long-term growth",
                "riskTolerance", "aggressive"
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_SupportReview");

        complete(task(), withVariables(
                "dataComplete", true,
                "supportApproval", true,
                "supportComments", "Validated",
                "missingDataDescription", ""
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_ManagerApproval");
        assertThat(instance).variables().containsEntry("accountType", "PREMIUM");
        assertThat(instance).variables().containsEntry("requiresManagerApproval", true);
    }

    @Test
    void standardAccountPathRequestForMoreInformation() {

        DelegateHelpers.mockDelegate(delegateMocks.sendEmailDelegate())
                .setVariable("emailResult", "success")
                .mock();

        ProcessInstance instance = runtimeService().createProcessInstanceByKey("Customer_Onboarding_Process")
                .setVariable("customerId", "test-customer-1")
                .setVariable("accounts_url", "http://localhost:8080")
                .setVariable("apiToken", "test-token")
                .execute();

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");

        complete(task(), withVariables(
                "customerName", "Jane Doe",
                "customerEmail", "jane.doe@example.com",
                "customerPhone", "5551234567",
                "customerAddress", "100 Main St",
                "taxId", "123-45-6789",
                "dateOfBirth", "",
                "personalInvestments", "Index Funds",
                "iraAccounts", "Traditional IRA",
                "retirementAccounts", "401k",
                "totalAssetValue", 750000L,
                "investmentGoals", "Long-term growth",
                "riskTolerance", "moderate"
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_SupportReview");

        complete(task(), withVariables(
                "dataComplete", false,
                "supportApproval", false,
                "supportComments", "Data provided is not valid.",
                "missingDataDescription", "Date of Birth is required and must be in the format YYYY-MM-DD."
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");
        assertThat(instance).variables().containsEntry("dataComplete", false);
    }

    @Test
    void premiumAccountPathRequestForMoreInformation() {
        DelegateHelpers.mockDelegate(delegateMocks.sendEmailDelegate())
                .setVariable("emailResult", "success")
                .mock();

        ProcessInstance instance = runtimeService().createProcessInstanceByKey("Customer_Onboarding_Process")
                .setVariable("customerId", "test-customer-1")
                .setVariable("accounts_url", "http://localhost:8080")
                .setVariable("apiToken", "test-token")
                .execute();

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");

        complete(task(), withVariables(
                "customerName", "Jane Doe",
                "customerEmail", "jane.doe@example.com",
                "customerPhone", "5551234567",
                "customerAddress", "100 Main St",
                "taxId", "123456-789",
                "dateOfBirth", "1990-01-01",
                "personalInvestments", "Index Funds",
                "iraAccounts", "Traditional IRA",
                "retirementAccounts", "401k",
                "totalAssetValue", 1500000L,
                "investmentGoals", "Long-term growth",
                "riskTolerance", "aggressive"
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_SupportReview");

        complete(task(), withVariables(
                "dataComplete", true,
                "supportApproval", true,
                "supportComments", "Validated",
                "missingDataDescription", ""
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_ManagerApproval");
        assertThat(instance).variables().containsEntry("accountType", "PREMIUM");
        assertThat(instance).variables().containsEntry("requiresManagerApproval", true);

        String rejectionReason = "More information required.";
        complete(task(), withVariables("managerApproval", false,
                "managerComments", "For premium accounts we require proof of income. Please provide your most recent tax return.",
                                    "rejectionReason", rejectionReason));
        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");
        assertThat(instance).variables().containsEntry("managerApproval", false);
        assertThat(instance).variables().containsEntry("rejectionReason", rejectionReason);
    }

    @Test
    void standardAccountOpenAccountFails() {

        DelegateHelpers.mockDelegate(delegateMocks.sendEmailDelegate())
                .setVariable("emailResult", "success")
                .mock();
        DelegateHelpers.mockDelegate(delegateMocks.sendSMSDelegate())
                .setVariable("smsResult", "success")
                .mock();

        stubFor(post(urlEqualTo("/create")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("accountCreateFailedResponse.json")));

        ProcessInstance instance = runtimeService().createProcessInstanceByKey("Customer_Onboarding_Process")
                .setVariable("customerId", "test-customer-1")
                .setVariable("accounts_url", "http://localhost:8080")
                .setVariable("apiToken", "test-token")
                .execute();

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");

        complete(task(), withVariables(
                "customerName", "Jane Doe",
                "customerEmail", "jane.doe@example.com",
                "customerPhone", "5551234567",
                "customerAddress", "100 Main St",
                "taxId", "123-45-6789",
                "dateOfBirth", "1990-01-01",
                "personalInvestments", "Index Funds",
                "iraAccounts", "Traditional IRA",
                "retirementAccounts", "401k",
                "totalAssetValue", 750000L,
                "investmentGoals", "Long-term growth",
                "riskTolerance", "moderate"
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_SupportReview");

        complete(task(), withVariables(
                "dataComplete", true,
                "supportApproval", true,
                "supportComments", "Validated",
                "missingDataDescription", ""
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).hasPassed("Task_CheckAssetValue");
        assertThat(instance).variables().containsEntry("accountType", "STANDARD");
        assertThat(instance).hasPassed("Task_OpenAccount");
        assertThat(instance).hasPassed("Event_AccountCreationError");

    }

    @Test
    void standardAccountSmsFails() {

        DelegateHelpers.mockDelegate(delegateMocks.sendEmailDelegate())
                .setVariable("emailResult", "success")
                .mock();
        DelegateHelpers.mockDelegate(delegateMocks.sendSMSDelegate())
                .setVariable("smsResult", "error")
                .throwsException(new BpmnError("500", "SMS service is down"))
                .mock();

        stubFor(post(urlEqualTo("/create")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("accountCreateResponse.json")));

        ProcessInstance instance = runtimeService().createProcessInstanceByKey("Customer_Onboarding_Process")
                .setVariable("customerId", "test-customer-1")
                .setVariable("accounts_url", "http://localhost:8080")
                .setVariable("apiToken", "test-token")
                .execute();

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_CaptureCustomerData");

        complete(task(), withVariables(
                "customerName", "Jane Doe",
                "customerEmail", "jane.doe@example.com",
                "customerPhone", "5551234567",
                "customerAddress", "100 Main St",
                "taxId", "123-45-6789",
                "dateOfBirth", "1990-01-01",
                "personalInvestments", "Index Funds",
                "iraAccounts", "Traditional IRA",
                "retirementAccounts", "401k",
                "totalAssetValue", 750000L,
                "investmentGoals", "Long-term growth",
                "riskTolerance", "moderate"
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).isWaitingAt("Task_SupportReview");

        complete(task(), withVariables(
                "dataComplete", true,
                "supportApproval", true,
                "supportComments", "Validated",
                "missingDataDescription", ""
        ));

        advanceFlowUntilAction(instance.getId());
        assertThat(instance).hasPassed("Task_CheckAssetValue");
        assertThat(instance).variables().containsEntry("accountType", "STANDARD");
        assertThat(instance).hasPassed("Task_OpenAccount");
        assertThat(instance).hasPassed("Task_LogAccountCreation");
        assertThat(instance).hasPassed("Task_SendSMSConfirmation");
        assertThat(instance).hasPassed("Task_LogSMSError");

    }

    private static Document loadBpmnDocument() throws Exception {
        try (InputStream inputStream = AccountOpeningTest.class.getResourceAsStream("/AccountOpening-InvestmentAccount.bpmn")) {
            assertNotNull(inputStream, "BPMN resource not found");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private static Map<String, String> getFormFieldTypes(Document document, String userTaskId) throws Exception {
        XPath xpath = createXpath();
        String expression = String.format("//bpmn:userTask[@id='%s']/bpmn:extensionElements/camunda:formData/camunda:formField", userTaskId);
        NodeList fieldNodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
        assertNotNull(fieldNodes, "No form fields found for task " + userTaskId);
        assertTrue(fieldNodes.getLength() > 0, "No form fields found for task " + userTaskId);

        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Node fieldNode = fieldNodes.item(i);
            NamedNodeMap attributes = fieldNode.getAttributes();
            Node idNode = attributes.getNamedItem("id");
            Node typeNode = attributes.getNamedItem("type");
            if (idNode != null && typeNode != null) {
                fieldTypes.put(idNode.getNodeValue(), typeNode.getNodeValue());
            }
        }
        return fieldTypes;
    }

    private static List<String> getEnumValueIds(Document document, String userTaskId, String fieldId) throws Exception {
        XPath xpath = createXpath();
        String expression = String.format(
                "//bpmn:userTask[@id='%s']/bpmn:extensionElements/camunda:formData/camunda:formField[@id='%s']/camunda:value",
                userTaskId,
                fieldId
        );
        NodeList valueNodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
        assertNotNull(valueNodes, "No enum values found for field " + fieldId);
        assertTrue(valueNodes.getLength() > 0, "No enum values found for field " + fieldId);

        List<String> valueIds = new ArrayList<>();
        for (int i = 0; i < valueNodes.getLength(); i++) {
            Node valueNode = valueNodes.item(i);
            Node idNode = valueNode.getAttributes().getNamedItem("id");
            if (idNode != null) {
                valueIds.add(idNode.getNodeValue());
            }
        }
        return valueIds;
    }

    private static XPath createXpath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if ("bpmn".equals(prefix)) {
                    return "http://www.omg.org/spec/BPMN/20100524/MODEL";
                }
                if ("camunda".equals(prefix)) {
                    return "http://camunda.org/schema/1.0/bpmn";
                }
                return XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return java.util.Collections.emptyIterator();
            }
        });
        return xpath;
    }
}
