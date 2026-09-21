package org.finos.fluxnova.bpm.test.plugin.impl;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.coverage.CoverageProperties;
import org.finos.fluxnova.bpm.test.domain.coverage.ProcessTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.ModelTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.report.ExternalScriptCoverageReport;
import org.finos.fluxnova.bpm.test.plugin.domain.report.ModelCoverageReport;
import org.finos.fluxnova.bpm.test.plugin.domain.script.*;
import org.finos.fluxnova.bpm.test.plugin.impl.scripts.ScriptRunsExtractor;
import org.finos.fluxnova.bpm.test.plugin.impl.sonar.SonarCoverageReporter;
import org.finos.fluxnova.bpm.test.plugin.utils.TestData;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

import static org.finos.fluxnova.bpm.test.plugin.utils.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverageCollectorTest {

    @Mock
    MetadataProducer metadataProducer;

    @Mock
    ScriptRunsExtractor scriptRunsExtractor;

    @Mock
    MavenSession mavenSession;

    @Mock
    MavenExecutionResult mavenExecutionResult;

    @Mock
    SonarCoverageReporter sonarCoverageReporter;

    @InjectMocks
    CoverageCollector coverageCollector;

    private static MockedStatic<LoggerFactory> loggerFactoryMock;
    private static Logger logger;

    private final Path buildDir = Path.of("target");
    private final Path srcDir = Path.of("src");

    @BeforeAll
    static void setup() {
        deleteTestGeneratedFiles();
        logger = mock(Logger.class);
        loggerFactoryMock = mockStatic(LoggerFactory.class);
        loggerFactoryMock.when(() -> LoggerFactory.getLogger(anyString())).thenReturn(logger);
    }

    @BeforeEach
    void before() {
        mockMavenSetError();
    }


    @AfterAll
    static void cleanup() {
        deleteTestCoverageFiles();
        deleteTestGeneratedFiles();
        loggerFactoryMock.reset();
        loggerFactoryMock.close();
    }

    @Test
    void testCollect__process_test_gets100ForOneRunWithFullProcessCoverage_noScriptTests() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            ScriptMetadata scriptMetadata = new ScriptMetadata(0, new ArrayList<>());
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport coverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(100.0, coverageReport.processCoverage());
            assertEquals("OrderDemo", coverageReport.metadata().processDefinitionKey());
            assertEquals("first_process.bpmn", coverageReport.metadata().name());
            assertFalse(coverageReport.pass());
        }
    }

    @Test
    void testCollect_scriptCoverageBelow_buildFailsByDefault() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(false);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            ScriptMetadata scriptMetadata = new ScriptMetadata(0, new ArrayList<>());
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport coverageReport = readModelCoverageFile("first_process.bpmn");
            assertFalse(coverageReport.pass());
            verify(mavenExecutionResult, times(1)).addException(any());
        }
    }

    @Test
    void testCollect_processCoverageBelow_buildFailsByDefault() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(false);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            List<String> coveredLines = Arrays.asList("1", "2", "3", "4", "5");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLines);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLines);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            modelTestMetadata.setProcessTestMetadata(null);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertFalse(modelCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(2)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();

            // verify maven build error for low coverage
            verify(mavenExecutionResult, times(1)).addException(any());
        }
    }

    @Test
    void testCollect_externalScriptBelow_buildFailsByDefault() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(false);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            List<String> coveredLinesInFirstScript = Arrays.asList("1", "2", "3", "4");
            List<String> coveredLinesInSecondScript = Arrays.asList("1", "3", "4", "5");
            List<String> coveredLinesInExternalScript = Arrays.asList("1", "2");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLinesInFirstScript);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLinesInSecondScript);
            ScriptRunInstance externalScript = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLinesInExternalScript, 4);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript, externalScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of("test_one.groovy", new ExternalScriptMetadata("test_one.groovy", null, List.of("1", "2", "3", "4")));
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertTrue(modelCoverageReport.pass());

            ExternalScriptCoverageReport firstExternalScriptCoverageReport = readExternalScriptCoverageFile("test_one.groovy");
            assertFalse(firstExternalScriptCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(3)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();

            verify(mavenExecutionResult, times(1)).addException(any());
        }
    }

    @Test
    void testCollect_coveragesMet_buildSucceedsByDefault() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(false);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            List<String> coveredLinesInFirstScript = Arrays.asList("1", "2", "3", "4");
            List<String> coveredLinesInSecondScript = Arrays.asList("1", "3", "4", "5");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLinesInFirstScript);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLinesInSecondScript);
            ScriptRunInstance externalScript = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLinesInSecondScript, 5);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript, externalScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of("test_one.groovy", new ExternalScriptMetadata("test_one.groovy", "/dummy", List.of("1", "2", "3", "4", "5")));
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertTrue(modelCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(3)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();

            verify(mavenExecutionResult, times(0)).addException(any());
        }
    }

    @Test
    void testCollect_coveragesNotMet_buildSucceedsWhenIgnoreCoverageSetToTrue() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            List<String> coveredLines = Arrays.asList("1", "2", "3", "4", "5");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLines);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLines);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            modelTestMetadata.setProcessTestMetadata(null);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertFalse(modelCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(2)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();

            verify(mavenExecutionResult, times(0)).addException(any());
        }
    }

    @Test
    void testCollect__process_test_gets100ForTwoRunsWithHalfProcessCoverageCoverageEach_noScriptTests() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_TWO_RUNS_SAMPLE);
            ScriptMetadata scriptMetadata = new ScriptMetadata(0, new ArrayList<>());
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport coverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(100.0, coverageReport.processCoverage());
            assertEquals("OrderDemo", coverageReport.metadata().processDefinitionKey());
            assertEquals("first_process.bpmn", coverageReport.metadata().name());
            assertFalse(coverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(0)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect__process_test_gets60WhenThreeOutOfFiveShapedHit() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_UNDER_100_COVERAGE_SAMPLE);
            ScriptMetadata scriptMetadata = new ScriptMetadata(0, new ArrayList<>());
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport coverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(60.0, coverageReport.processCoverage());
            assertEquals("OrderDemo", coverageReport.metadata().processDefinitionKey());
            assertEquals("first_process.bpmn", coverageReport.metadata().name());
            assertFalse(coverageReport.pass());
        }
    }

    @Test
    void testCollect__script_tests_gets100WhenAllScriptsCovered_noProcessTests() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            List<String> coveredLines = Arrays.asList("1", "2", "3", "4", "5");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLines);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLines);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            modelTestMetadata.setProcessTestMetadata(null);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(0.0, modelCoverageReport.processCoverage());
            assertEquals(100.0, modelCoverageReport.scriptCoverage());
            ScriptCoverage firstScriptCoverage = modelCoverageReport.scripts().get(0);
            assertEquals("activityOne", firstScriptCoverage.activityId());
            assertEquals(100.0, firstScriptCoverage.coverage());
            ScriptCoverage secondScriptCoverage = modelCoverageReport.scripts().get(1);
            assertEquals("activityTwo", secondScriptCoverage.activityId());
            assertEquals(100.0, secondScriptCoverage.coverage());
            assertFalse(modelCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(2)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect__script_and_process_tests_getsCoveragesForModelAndExternalScripts() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            List<String> coveredLinesInFirstScript = Arrays.asList("1", "2", "3");
            List<String> coveredLinesInSecondScript = Arrays.asList("1", "4");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLinesInFirstScript);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLinesInSecondScript);
            ScriptRunInstance externalScript = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLinesInSecondScript, 8);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript, externalScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of("test_one.groovy", new ExternalScriptMetadata("test_one.groovy", null, List.of("1", "2", "3", "4", "5", "6", "7", "8")));
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(100.0, modelCoverageReport.processCoverage());
            assertEquals(50.0, modelCoverageReport.scriptCoverage());
            ScriptCoverage firstScriptCoverage = modelCoverageReport.scripts().get(0);
            assertEquals("activityOne", firstScriptCoverage.activityId());
            assertEquals(60.0, firstScriptCoverage.coverage());
            ScriptCoverage secondScriptCoverage = modelCoverageReport.scripts().get(1);
            assertEquals("activityTwo", secondScriptCoverage.activityId());
            assertEquals(40.0, secondScriptCoverage.coverage());
            assertFalse(modelCoverageReport.pass());

            ExternalScriptCoverageReport firstExternalScriptCoverageReport = readExternalScriptCoverageFile("test_one.groovy");
            assertEquals(25.0, firstExternalScriptCoverageReport.coverage());
            assertFalse(firstExternalScriptCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(3)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect__thresholdSetTo0_reportsAllPass() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(0.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            List<String> coveredLinesInFirstScript = Arrays.asList("1", "2");
            List<String> coveredLinesInSecondScript = Arrays.asList("1", "2");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLinesInFirstScript);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLinesInSecondScript);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, false);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of("missing_tests.groovy", new ExternalScriptMetadata("missing_tests.groovy", null, null));
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertTrue(modelCoverageReport.pass());

            ExternalScriptCoverageReport firstExternalScriptCoverageReport = readExternalScriptCoverageFile("missing_tests.groovy");
            assertTrue(firstExternalScriptCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(3)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();

            verify(mavenExecutionResult, times(0)).addException(any());
        }
    }

    @Test
    void testCollect__script_and_process_tests_getsCoveragesForModelAndExternalScripts_overThreshold() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            List<String> coveredLinesInFirstScript = Arrays.asList("1", "2", "3", "4");
            List<String> coveredLinesInSecondScript = Arrays.asList("1", "3", "4", "5");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLinesInFirstScript);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLinesInSecondScript);
            ScriptRunInstance externalScript = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLinesInSecondScript, 5);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript, externalScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of("missing_tests.groovy", new ExternalScriptMetadata("missing_tests.groovy", "/dummy", List.of("1", "2", "3", "4", "5")));
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(100.0, modelCoverageReport.processCoverage());
            assertEquals(80.0, modelCoverageReport.scriptCoverage());
            ScriptCoverage firstScriptCoverage = modelCoverageReport.scripts().get(0);
            assertEquals("activityOne", firstScriptCoverage.activityId());
            assertEquals(80.0, firstScriptCoverage.coverage());
            ScriptCoverage secondScriptCoverage = modelCoverageReport.scripts().get(1);
            assertEquals("activityTwo", secondScriptCoverage.activityId());
            assertEquals(80.0, secondScriptCoverage.coverage());
            assertTrue(modelCoverageReport.pass());

            ExternalScriptCoverageReport firstExternalScriptCoverageReport = readExternalScriptCoverageFile("test_one.groovy");
            assertEquals(80.0, firstExternalScriptCoverageReport.coverage());
            assertTrue(firstExternalScriptCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(3)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect__whenExternalScriptMetadataMissingForOneFile_onlyGetsCoverageForOtherFile() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            List<String> coveredLines = Arrays.asList("1", "2", "3");
            ScriptRunInstance invalidMissingExternalScript = buildMockedExternalScriptRunInstance("invalid_missing_one.groovy", coveredLines, 4);
            ScriptRunInstance validExternalScript = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLines, 5);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(invalidMissingExternalScript, validExternalScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(List.of());
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of("test_one.groovy", new ExternalScriptMetadata("test_one.groovy", "/dummy", List.of("1", "2", "3", "4", "5")));
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then

            verify(logger, times(1))
                    .error("Error encountered in computing coverage for external script: {} :: {}", "invalid_missing_one.groovy", "External script metadata not found");

            ExternalScriptCoverageReport validExternalScriptCoverageReport = readExternalScriptCoverageFile("test_one.groovy");
            assertEquals(60.0, validExternalScriptCoverageReport.coverage());
            assertFalse(validExternalScriptCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(1)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect__scripts_and_process_tests_gets60InProcessAndLessInScripts() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            createFluxnovaReportFile(TestData.FLUXNOVA_REPORT_FULL_COVERAGE_ONE_RUN_SAMPLE);
            List<String> coveredLinesInFirstScript = Arrays.asList("1", "2", "3");
            List<String> coveredLinesInSecondScript = Arrays.asList("1", "4");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("AnotherProcess", "activityOne", coveredLinesInFirstScript);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLinesInSecondScript);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(100.0, modelCoverageReport.processCoverage());
            assertEquals(20.0, modelCoverageReport.scriptCoverage());
            ScriptCoverage firstScriptCoverage = modelCoverageReport.scripts().get(0);
            assertEquals("activityOne", firstScriptCoverage.activityId());
            assertEquals(0.0, firstScriptCoverage.coverage());
            ScriptCoverage secondScriptCoverage = modelCoverageReport.scripts().get(1);
            assertEquals("activityTwo", secondScriptCoverage.activityId());
            assertEquals(40.0, secondScriptCoverage.coverage());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(2)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect_errorInModelSetsErrorInCoverage() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", null, true);
            modelTestMetadata.setError("error in parsing model");
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals("error in parsing model", modelCoverageReport.error());
            assertEquals("first_process.bpmn", modelCoverageReport.metadata().name());
            assertEquals(0.0, modelCoverageReport.processCoverage());
            assertEquals(0.0, modelCoverageReport.scriptCoverage());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(0)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect_errorInScriptSetsErrorInThatScriptOnly() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            List<String> coveredLines = Arrays.asList("1", "2", "3", "4", "5");
            ScriptRunInstance firstScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityOne", coveredLines);
            ScriptRunInstance secondScript = buildMockedInlineScriptRunInstance("OrderDemo", "activityTwo", coveredLines);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScript, secondScript);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            ScriptMetadata scriptMetadata = buildScriptMetadata();
            List<ScriptMetrics> scriptMetrics = scriptMetadata.scriptCoverageMetadata();
            ScriptMetrics erroredScriptMetric = new ScriptMetrics("errorActivity", "groovy", null, "error in script");
            scriptMetrics.add(erroredScriptMetric);
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            modelTestMetadata.setProcessTestMetadata(null);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ModelCoverageReport modelCoverageReport = readModelCoverageFile("first_process.bpmn");
            assertEquals(0.0, modelCoverageReport.processCoverage());
            assertEquals(100.0, modelCoverageReport.scriptCoverage());
            ScriptCoverage firstScriptCoverage = modelCoverageReport.scripts().get(0);
            assertEquals("activityOne", firstScriptCoverage.activityId());
            assertEquals(100.0, firstScriptCoverage.coverage());
            assertNull(firstScriptCoverage.error());
            ScriptCoverage secondScriptCoverage = modelCoverageReport.scripts().get(1);
            assertEquals("activityTwo", secondScriptCoverage.activityId());
            assertEquals(100.0, secondScriptCoverage.coverage());
            assertNull(secondScriptCoverage.error());
            ScriptCoverage erroredScriptCoverage = modelCoverageReport.scripts().get(2);
            assertEquals(0.0, erroredScriptCoverage.coverage());
            assertEquals("error in script", erroredScriptCoverage.error());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(2)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect__ext_scripts_gets100InOne50InOneAnd0InOne() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            List<String> coveredLinesInFirstTest = Arrays.asList("1", "2", "3");
            List<String> coveredLinesInSecondTest = Arrays.asList("1", "4", "5", "6");
            ScriptRunInstance firstScriptFirstTestRun = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLinesInFirstTest, 6);
            ScriptRunInstance firstScriptSecondTestRun = buildMockedExternalScriptRunInstance("test_one.groovy", coveredLinesInSecondTest, 6);
            ScriptRunInstance secondScriptFirstTestRun = buildMockedExternalScriptRunInstance("test_two.groovy", coveredLinesInFirstTest, 6);
            List<ScriptRunInstance> scriptRunInstances = Arrays.asList(firstScriptFirstTestRun, firstScriptSecondTestRun, secondScriptFirstTestRun);
            when(scriptRunsExtractor.extractAll(any(), any())).thenReturn(scriptRunInstances);
            Map<String, ExternalScriptMetadata> externalScriptsOnClasspath = Map.of(
                    "test_one.groovy", new ExternalScriptMetadata("test_one.groovy", "dummy", List.of("1", "2", "3", "4", "5", "6")),
                    "test_two.groovy", new ExternalScriptMetadata("test_two.groovy", "dummy", List.of("1", "2", "3", "4", "5", "6")),
                    "missing_tests.groovy", new ExternalScriptMetadata("missing_tests.groovy", "dummy", List.of("1", "2", "3", "4", "5", "6"))
            );
            when(metadataProducer.extractAllExternalScripts(any())).thenReturn(externalScriptsOnClasspath);
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            ExternalScriptCoverageReport firstExternalScriptCoverageReport = readExternalScriptCoverageFile("test_one.groovy");
            assertEquals(100.0, firstExternalScriptCoverageReport.coverage());
            assertEquals("test_one.groovy", firstExternalScriptCoverageReport.metadata().name());
            assertTrue(firstExternalScriptCoverageReport.pass());
            ExternalScriptCoverageReport secondExternalScriptCoverageReport = readExternalScriptCoverageFile("test_two.groovy");
            assertEquals(50.0, secondExternalScriptCoverageReport.coverage());
            assertEquals("test_two.groovy", secondExternalScriptCoverageReport.metadata().name());
            assertFalse(secondExternalScriptCoverageReport.pass());
            ExternalScriptCoverageReport externalWithNoTestsScriptCoverageReport = readExternalScriptCoverageFile("missing_tests.groovy");
            assertEquals(0.0, externalWithNoTestsScriptCoverageReport.coverage());
            assertEquals("missing_tests.groovy", externalWithNoTestsScriptCoverageReport.metadata().name());
            assertFalse(externalWithNoTestsScriptCoverageReport.pass());

            // verify expected executions for sonar report generation
            verify(sonarCoverageReporter, times(3)).addSonarCoverageElement(any());
            verify(sonarCoverageReporter, times(1)).toSonarXML();
        }
    }

    @Test
    void testCollect_logsWarningWhenNoBpmnsFoundInProject() {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            Path buildDir = Path.of("test");
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            verify(logger, atLeastOnce()).warn("No bpmn files found in project");
        }
    }

    @Test
    void testCollect_logsErrorWhenNoFluxnovaReportFileFound() throws IOException {
        try (MockedStatic<CoverageProperties> coveragePropertiesMockedStatic = Mockito.mockStatic(CoverageProperties.class)) {
            coveragePropertiesMockedStatic.when(CoverageProperties::getIgnoreCoverageFailure).thenReturn(true);
            coveragePropertiesMockedStatic.when(CoverageProperties::getThreshold).thenReturn(80.0);
            deleteFluxnovaReportFile();
            ScriptMetadata scriptMetadata = new ScriptMetadata(0, new ArrayList<>());
            ModelTestMetadata modelTestMetadata = buildMockedModelTestMetadata("OrderDemo", scriptMetadata, true);
            when(metadataProducer.aggregate(any(), any(), any())).thenReturn(Collections.singletonList(modelTestMetadata));
            //When
            coverageCollector.collect(buildDir, srcDir, mavenSession);
            //Then
            verify(logger, atLeast(1))
                    .error("An error was encountered in computing process coverage for {} :: {}",
                            "OrderDemo",
                            "Fluxnova process test report file not found for org.finos.fluxnova.bpm.test.plugin.impl.CoverageCollectorTest"
                    );
        }
    }

    @Test
    void testCollect_handlesOverallExceptionCorrectly() {
        when(metadataProducer.aggregate(any(), any(), any())).thenThrow(new RuntimeException("Error"));
        coverageCollector.collect(buildDir, srcDir, mavenSession);
        verify(logger, times(1))
                .error(eq("Exception thrown while computing coverage"), any(Exception.class));
    }

    private static void createFluxnovaReportFile(String fluxnovaReport) throws IOException {
        Path fluxnovaReportPath = Path.of("target", "process-test-coverage",
                "org.finos.fluxnova.bpm.test.plugin.impl.CoverageCollectorTest",
                "report.json");
        createFile(fluxnovaReportPath, fluxnovaReport);
    }

    private static void deleteFluxnovaReportFile() throws IOException {
        try {
            Path fluxnovaReportPath = Path.of("target", "process-test-coverage",
                    "org.finos.fluxnova.bpm.test.plugin.impl.CoverageCollectorTest",
                    "report.json");
            Files.delete(fluxnovaReportPath);
            Path fluxnovaPath = Path.of("target", "process-test-coverage",
                    "org.finos.fluxnova.bpm.test.plugin.impl.CoverageCollectorTest");
            Files.delete(fluxnovaPath);
        } catch (NoSuchFileException e) {
            // do nothing
        }
    }

    private static ModelCoverageReport readModelCoverageFile(String fileName) throws IOException {
        Path fluxnovaReportPath = Path.of("target", "coverage-collection",
                "code-coverage",
                fileName + ".coverage.json");
        File report = fluxnovaReportPath.toFile();
        return new ObjectMapper().readValue(report, ModelCoverageReport.class);
    }

    private static ExternalScriptCoverageReport readExternalScriptCoverageFile(String fileName) throws IOException {
        Path fluxnovaReportPath = Path.of("target", "coverage-collection",
                "code-coverage",
                fileName + ".coverage.json");
        File report = fluxnovaReportPath.toFile();
        return new ObjectMapper().readValue(report, ExternalScriptCoverageReport.class);
    }

    private static ModelTestMetadata buildMockedModelTestMetadata(String processDefinitionKey, ScriptMetadata scriptMetadata, boolean includeProcessTests) {
        ModelTestMetadata modelTestMetadata = new ModelTestMetadata();
        modelTestMetadata.setFileName("first_process.bpmn");
        modelTestMetadata.setProcessDefinitionKey(processDefinitionKey);
        modelTestMetadata.setScriptMetadata(scriptMetadata);
        if (includeProcessTests) {
            ProcessTestMetadata processTestMetadata = new ProcessTestMetadata("OrderDemo", "org.finos.fluxnova.bpm.test.plugin.impl.CoverageCollectorTest");
            modelTestMetadata.setProcessTestMetadata(processTestMetadata);
        }
        return modelTestMetadata;
    }

    private static ScriptMetadata buildScriptMetadata() {
        return new ScriptMetadata(10, buildScriptMetricMetadata());
    }

    private static ScriptRunInstance buildMockedExternalScriptRunInstance(String scriptName, List<String> coveredLines, Integer totalLines) {
        return new ScriptRunInstance(scriptName, null, null, totalLines, coveredLines, true);
    }

    private static ScriptRunInstance buildMockedInlineScriptRunInstance(String processDefinitionKey, String activityId, List<String> coveredLines) {
        return new ScriptRunInstance(null, activityId, processDefinitionKey, null, coveredLines, false);
    }

    private static List<ScriptMetrics> buildScriptMetricMetadata() {
        List<String> totalLines = Arrays.asList("1", "2", "3", "4", "5");
        List<ScriptMetrics> scriptMetrics = new ArrayList<>();
        ScriptMetrics firstScriptMetric = new ScriptMetrics("activityOne", "groovy", totalLines, null);
        ScriptMetrics secondScriptMetric = new ScriptMetrics("activityTwo", "groovy", totalLines, null);
        scriptMetrics.add(firstScriptMetric);
        scriptMetrics.add(secondScriptMetric);
        return scriptMetrics;
    }

    private void mockMavenSetError() {
        lenient().when(mavenSession.getResult()).thenReturn(mavenExecutionResult);
        lenient().when(mavenExecutionResult.getExceptions()).thenReturn(new ArrayList<>());
    }
}