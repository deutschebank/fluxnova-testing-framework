package org.finos.fluxnova.bpm.test.plugin.impl;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.coverage.CoverageProperties;
import org.finos.fluxnova.bpm.test.coverage.ProcessCoverage;
import org.finos.fluxnova.bpm.test.domain.coverage.ProcessTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.ModelTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ExternalScriptMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ScriptMetrics;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ScriptMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.finos.fluxnova.bpm.test.plugin.utils.Constants.COVERAGE_COLLECTION_DIRECTORY;
import static org.finos.fluxnova.bpm.test.plugin.utils.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataProducerTest {

    private static MockedStatic<LoggerFactory> loggerFactoryMock;
    private static MockedStatic<CoverageProperties> coveragePropertiesMock;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path buildDir = Path.of("target");
    private static final Path srcDir = Path.of("src");

    @InjectMocks
    MetadataProducer metadataProducer;

    @BeforeAll
    static void setup() {
        coveragePropertiesMock = mockStatic(CoverageProperties.class);
        coveragePropertiesMock.when(CoverageProperties::getSkipCoverage).thenReturn(false);
        ProcessCoverage.register("OrderDemo", MetadataProducerTest.class);
        ProcessCoverage.register("SomeInvalidScripts", MetadataProducerTest.class);
        Logger logger = mock(Logger.class);
        loggerFactoryMock = mockStatic(LoggerFactory.class);
        loggerFactoryMock.when(() -> LoggerFactory.getLogger(anyString())).thenReturn(logger);
    }

    @AfterAll
    static void cleanup() {
        deleteTestGeneratedFiles();
        deleteTestCoverageFiles();
        loggerFactoryMock.close();
    }

    @Test
    void aggregate_collectsProcessTestAndScriptMetadataWhereAppropriate() {
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(buildDir, srcDir, objectMapper);
        ModelTestMetadata modelTestMetadataForBpmnWithProcessTestsAndValidScripts =
                modelUnderTest("1_valid_scripts.bpmn", modelTestMetadataList);
        ModelTestMetadata modelTestMetadataForBpmnWithNoProcessTestsButValidScripts =
                modelUnderTest("2_valid_scripts.bpmn", modelTestMetadataList);

        assertEquals("1_valid_scripts.bpmn",
                modelTestMetadataForBpmnWithProcessTestsAndValidScripts.getFileName());
        assertEquals("OrderDemo",
                modelTestMetadataForBpmnWithProcessTestsAndValidScripts.getProcessDefinitionKey());

        ScriptMetadata orderDemoScriptMetadata = modelTestMetadataForBpmnWithProcessTestsAndValidScripts.getScriptMetadata();
        assertEquals(4, orderDemoScriptMetadata.totalLines());
        ScriptMetrics orderDemoFirstScriptMetrics = orderDemoScriptMetadata.scriptCoverageMetadata().get(0);
        assertEquals("Activity_1defe3", orderDemoFirstScriptMetrics.activityId());
        assertEquals(3, orderDemoFirstScriptMetrics.lines().size());
        assertNull(orderDemoFirstScriptMetrics.error());
        ScriptMetrics orderDemoSecondScriptMetrics = orderDemoScriptMetadata.scriptCoverageMetadata().get(1);
        assertEquals("Activity_d6s2raf", orderDemoSecondScriptMetrics.activityId());
        assertEquals(1, orderDemoSecondScriptMetrics.lines().size());
        assertNull(orderDemoSecondScriptMetrics.error());
        ProcessTestMetadata processTestMetadata = modelTestMetadataForBpmnWithProcessTestsAndValidScripts.getProcessTestMetadata();
        assertEquals("org.finos.fluxnova.bpm.test.plugin.impl.MetadataProducerTest", processTestMetadata.testClass());

        ScriptMetadata anotherDemoScriptMetadata = modelTestMetadataForBpmnWithNoProcessTestsButValidScripts.getScriptMetadata();
        assertEquals(11, anotherDemoScriptMetadata.totalLines());
        ScriptMetrics anotherDemoFirstScriptMetrics = anotherDemoScriptMetadata.scriptCoverageMetadata().get(0);
        assertEquals("Activity_0jycfse", anotherDemoFirstScriptMetrics.activityId());
        assertEquals(8, anotherDemoFirstScriptMetrics.lines().size());
        ScriptMetrics anotherDemoSecondScriptMetrics = anotherDemoScriptMetadata.scriptCoverageMetadata().get(1);
        assertEquals("Activity_08qcs3s", anotherDemoSecondScriptMetrics.activityId());
        assertEquals(3, anotherDemoSecondScriptMetrics.lines().size());
        assertNull(anotherDemoFirstScriptMetrics.error());
        assertNull(modelTestMetadataForBpmnWithNoProcessTestsButValidScripts.getProcessTestMetadata());
    }

    @Test
    void aggregate_createsTempFilesForInlineScripts() throws IOException {
        metadataProducer.aggregate(buildDir, srcDir, objectMapper);

        assertGeneratedFileContents("AnotherDemo_Activity_0jycfse.js", """
                var k=0;
                var j = 1+1;
                
                ContinueStatement(5);
                
                function ContinueStatement(input) {
                  let i = 0;
                  while (i < 10) {
                    if (i === input) {
                      i++;
                      continue;
                    }
                    console.log(i);
                    i++;
                  }
                }""");

        assertGeneratedFileContents("AnotherDemo_Activity_08qcs3s.groovy", """
                def i=0;
                
                while (i>0) {
                 if (i == 5) {
                   def j = j * 5;
                   return j;
                 }
                }""");

        assertGeneratedFileContents("ExternalAndInlineScript_Activity_1roy2a1.groovy", "def i = 2;");
        assertGeneratedFileContents("OrderDemo_Activity_1defe3.groovy", """
                def i=0;
                
                if (int j=0, j<10, j++) {
                    j = j + i
                }
                
                return i;""");
        assertGeneratedFileContents("OrderDemo_Activity_d6s2raf.groovy", "def i = 2;");

    }

    @Test
    void aggregate_doesNotCollectMetadataForEmptyOrNullScripts() {
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(buildDir, srcDir, objectMapper);
        ModelTestMetadata modelTestMetadataForBpmnWithSomeEmptyOrNullScripts =
                modelUnderTest("3_two_invalid_scripts.bpmn", modelTestMetadataList);

        assertEquals("3_two_invalid_scripts.bpmn",
                modelTestMetadataForBpmnWithSomeEmptyOrNullScripts.getFileName());
        assertEquals("SomeInvalidScripts",
                modelTestMetadataForBpmnWithSomeEmptyOrNullScripts.getProcessDefinitionKey());
        ScriptMetadata scriptMetadata = modelTestMetadataForBpmnWithSomeEmptyOrNullScripts.getScriptMetadata();
        assertEquals(4, scriptMetadata.totalLines());
        ScriptMetrics metadataForSomeNullOrEmptyScripts = scriptMetadata.scriptCoverageMetadata().get(0);
        assertEquals("Activity_1y5hugt", metadataForSomeNullOrEmptyScripts.activityId());
        ProcessTestMetadata processTestMetadata = modelTestMetadataForBpmnWithSomeEmptyOrNullScripts.getProcessTestMetadata();
        assertEquals("org.finos.fluxnova.bpm.test.plugin.impl.MetadataProducerTest", processTestMetadata.testClass());
        assertEquals(1, scriptMetadata.scriptCoverageMetadata().size());
    }

    @Test
    void aggregate_doesNotCollectMetadataForExternalScripts() {
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(buildDir, srcDir, objectMapper);
        ModelTestMetadata modelTestMetadataForBpmnWithExternalAndInlineScript =
                modelUnderTest("4_external_and_inline_scripts.bpmn", modelTestMetadataList);

        assertEquals("4_external_and_inline_scripts.bpmn",
                modelTestMetadataForBpmnWithExternalAndInlineScript.getFileName());
        assertEquals("ExternalAndInlineScript",
                modelTestMetadataForBpmnWithExternalAndInlineScript.getProcessDefinitionKey());
        ScriptMetadata scriptMetadata = modelTestMetadataForBpmnWithExternalAndInlineScript.getScriptMetadata();
        assertEquals(1, scriptMetadata.totalLines());
        ScriptMetrics metadataForValidInlineScript = scriptMetadata.scriptCoverageMetadata().get(0);
        assertEquals("Activity_1roy2a1", metadataForValidInlineScript.activityId());
        assertEquals(1, scriptMetadata.scriptCoverageMetadata().size());
    }

    @Test
    void aggregate_returnsEmptyListIfBpmnFilesNotFound() {
        Path anotherDir = Path.of("somewhere-else");
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(anotherDir, srcDir, objectMapper);
        assertEquals(0, modelTestMetadataList.size());
    }

    @Test
    void aggregate_setsMetadataToNullIfIssueWithBpmn() {
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(buildDir, srcDir, objectMapper);
        ModelTestMetadata modelTestMetadataForErroredBpmn =
                modelUnderTest("5_invalid_bpmn.bpmn", modelTestMetadataList);
        assertEquals("SAXException while parsing input stream", modelTestMetadataForErroredBpmn.getError());
        assertNull(modelTestMetadataForErroredBpmn.getScriptMetadata());
        assertNull(modelTestMetadataForErroredBpmn.getProcessTestMetadata());
    }

    @Test
    void aggregate_setProcessTestMetadataToNullIfIssueWithProcessTestMetadataFile() throws IOException {
        createInvalidProcessMetadataFile();
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(buildDir, srcDir, objectMapper);
        ModelTestMetadata modelTestMetadataForErroredBpmn =
                modelUnderTest("5_invalid_bpmn.bpmn", modelTestMetadataList);
        assertEquals("SAXException while parsing input stream", modelTestMetadataForErroredBpmn.getError());
        assertNull(modelTestMetadataForErroredBpmn.getScriptMetadata());
        assertNull(modelTestMetadataForErroredBpmn.getProcessTestMetadata());
    }

    @Test
    void aggregate_setsScriptCoverageMetadataErrorIfErrorEncounteredInParsingScript() {
        List<ModelTestMetadata> modelTestMetadataList = metadataProducer.aggregate(buildDir, srcDir, objectMapper);
        ModelTestMetadata modelTestMetadataForErroredScript =
                modelUnderTest("6_invalid_script.bpmn", modelTestMetadataList);
        ScriptMetadata allScripts = modelTestMetadataForErroredScript.getScriptMetadata();
        assertEquals(2, allScripts.totalLines());
        ScriptMetrics invalidScriptMetadata = allScripts.scriptCoverageMetadata().get(0);
        assertNotNull(invalidScriptMetadata.error());
        assertEquals("Activity_1gogk1q", invalidScriptMetadata.activityId());
        ScriptMetrics validScriptMetadata = allScripts.scriptCoverageMetadata().get(1);
        assertNull(validScriptMetadata.error());
        assertEquals("Activity_1gl41hw", validScriptMetadata.activityId());
        assertEquals(2, validScriptMetadata.lines().size());
    }

    @Test
    void extractAllExternalScripts_returnsAllGroovyAndJSFileNames() {
        Map<String, ExternalScriptMetadata> externalScripts = metadataProducer.extractAllExternalScripts(buildDir);
        assertEquals(4, externalScripts.size());

        ExternalScriptMetadata firstScript = findExternalScript("1_script.js", externalScripts);
        assertNotNull(firstScript);
        assertEquals("1_script.js", firstScript.fileName());
        assertTrue(firstScript.filePath().contains("unit" + File.separator + "1_script.js"));
        assertEquals(List.of("1", "2", "4"), firstScript.totalLines());

        ExternalScriptMetadata secondScript = findExternalScript("2_script.groovy", externalScripts);
        assertNotNull(secondScript);
        assertEquals("2_script.groovy", secondScript.fileName());
        assertTrue(secondScript.filePath().contains("unit" + File.separator + "2_script.groovy"));
        assertEquals(List.of("1", "4", "5"), secondScript.totalLines());

        ExternalScriptMetadata thirdScript = findExternalScript("3_script.groovy", externalScripts);
        assertNotNull(thirdScript);
        assertEquals("3_script.groovy", thirdScript.fileName());
        assertTrue(thirdScript.filePath().contains("unit" + File.separator + "test" + File.separator + "3_script.groovy"));
        assertEquals(List.of("1", "4", "6"), thirdScript.totalLines());

        ExternalScriptMetadata fourthScript = findExternalScript("4_script.groovy", externalScripts);
        assertNotNull(fourthScript);
        assertEquals("4_script.groovy", fourthScript.fileName());
        assertTrue(fourthScript.filePath().contains("unit" + File.separator + "test" + File.separator + "4_script.groovy"));
        assertEquals(List.of(), fourthScript.totalLines());
    }

    @Test
    void extractAllExternalScripts_doesNotReturnNodeModuleScripts() {
        Map<String, ExternalScriptMetadata> externalScripts = metadataProducer.extractAllExternalScripts(buildDir);
        assertEquals(4, externalScripts.size());
        assertNull(findExternalScript("test.js", externalScripts));
        assertNull(findExternalScript("test.groovy", externalScripts));
    }

    @Test
    void extractAllExternalScripts_doesNotReturnGroovyTestClasses() throws IOException {
        Path testClassesDir = buildDir.resolve("test-classes");
        Files.createDirectories(testClassesDir);

        Path groovyTestSpecPath = testClassesDir.resolve("TestSpec.groovy");
        Path groovyOtherPath = testClassesDir.resolve("Other.groovy");
        Path jsFilePath = testClassesDir.resolve("script.js");

        createFile(groovyTestSpecPath, "class Foo extends ScriptTestSpecification {}");
        createFile(groovyOtherPath, "class Bar {}");
        createFile(jsFilePath, "console.log('JS');");

        Map<String, ExternalScriptMetadata> externalScripts = metadataProducer.extractAllExternalScripts(buildDir);

        assertNull(findExternalScript("TestSpec.groovy", externalScripts), "Should exclude ScriptTestSpecification groovy file");
        assertNotNull(findExternalScript("Other.groovy", externalScripts), "Should include non-test groovy file");
        assertNotNull(findExternalScript("script.js", externalScripts), "Should include JS file");

        // Clean up only the files created by this test
        Files.deleteIfExists(groovyTestSpecPath);
        Files.deleteIfExists(groovyOtherPath);
        Files.deleteIfExists(jsFilePath);
    }

    private static void createInvalidProcessMetadataFile() throws IOException {
        Path path = Path.of(buildDir.toString(), COVERAGE_COLLECTION_DIRECTORY, "12121212.metadata.json");
        createFile(path, "invalid_content");
    }

    private static void assertGeneratedFileContents(String fileName, String expectedContent) throws IOException {
        String actualContent = getGeneratedFileContents(fileName);
        assertEquals(expectedContent, actualContent);
    }

    private static ExternalScriptMetadata findExternalScript(String scriptName, Map<String, ExternalScriptMetadata> externalScripts) {
        return externalScripts.get(scriptName);
    }
}