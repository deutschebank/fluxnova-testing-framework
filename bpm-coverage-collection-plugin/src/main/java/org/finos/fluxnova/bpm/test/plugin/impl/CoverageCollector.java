package org.finos.fluxnova.bpm.test.plugin.impl;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.TestException;
import org.finos.fluxnova.bpm.test.domain.coverage.ProcessTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.ModelTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.report.*;
import org.finos.fluxnova.bpm.test.plugin.domain.script.*;
import org.finos.fluxnova.bpm.test.plugin.domain.sonar.SonarScriptCoverageElement;
import org.finos.fluxnova.bpm.test.plugin.domain.suite.Event;
import org.finos.fluxnova.bpm.test.plugin.impl.process.FluxnovaMetricsExtractor;
import org.finos.fluxnova.bpm.test.plugin.impl.scripts.ScriptRunsExtractor;
import org.finos.fluxnova.bpm.test.plugin.impl.sonar.SonarCoverageReporter;
import org.finos.fluxnova.bpm.test.plugin.utils.TestResourceType;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.finos.fluxnova.bpm.test.coverage.CoverageProperties.getIgnoreCoverageFailure;
import static org.finos.fluxnova.bpm.test.coverage.CoverageProperties.getThreshold;
import static org.finos.fluxnova.bpm.test.plugin.utils.Constants.*;
import static org.finos.fluxnova.bpm.test.plugin.utils.Utils.*;
import static java.util.stream.Collectors.groupingBy;

public class CoverageCollector {

    private static final Logger logger = LoggerFactory.getLogger("coverage-collector");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Path buildPath;
    private Path srcPath;
    private MavenSession mavenSession;

    @Inject
    MetadataProducer metadataProducer;

    @Inject
    ScriptRunsExtractor scriptRunsExtractor;

    @Inject
    SonarCoverageReporter sonarCoverageReporter;

    public void collect(Path buildDir, Path srcDir, MavenSession mavenSession) {
        this.mavenSession = mavenSession;
        this.buildPath = buildDir;
        this.srcPath = srcDir;
        this.sonarCoverageReporter.init(this.buildPath, this.srcPath);
        try {
            logger.info("Coverage threshold set as {}\n", getThreshold());
            List<ScriptRunInstance> scriptRunInstances = scriptRunsExtractor.extractAll(buildPath, objectMapper);
            generateCoverageReportsForProcess(scriptRunInstances);
            generateCoverageReportsForExtScripts(buildPath, scriptRunInstances);
            this.sonarCoverageReporter.toSonarXML();
        } catch (Exception e) {
            deleteGeneratedInlineFiles(srcDir);
            logger.error("Exception thrown while computing coverage", e);
        }
    }

    private void generateCoverageReportsForExtScripts(Path buildPath, List<ScriptRunInstance> scriptRunInstances) {
        Map<String, ExternalScriptMetadata> externalScripts = metadataProducer.extractAllExternalScripts(buildPath);
        handleExternalScriptsWithNoTests(externalScripts, scriptRunInstances);
        handleExternalScriptsWithTests(externalScripts, scriptRunInstances);
    }

    private void handleExternalScriptsWithTests(Map<String, ExternalScriptMetadata> externalScripts, List<ScriptRunInstance> scriptRunInstances) {
        scriptRunInstances.stream().filter(ScriptRunInstance::isExternal).collect(groupingBy(ScriptRunInstance::scriptName)).forEach((scriptName, runInstances) -> {
            try {
                ExternalScriptMetadata externalScriptMetadata = getExternalScriptMetadata(scriptName, externalScripts);

                Set<String> coveredLines = runInstances.stream().flatMap(run -> run.coveredLines().stream()).collect(Collectors.toSet());

                double totalLines = runInstances.getFirst().totalLines();
                double coverage = calculateCoverage(totalLines, coveredLines.size());
                CoverageMetadata coverageMetadata = CoverageMetadata.setMetadataForExtScriptCoverage(scriptName);
                boolean coverageMet = checkCoverageForType(coverageMetadata, TestResourceType.EXTERNAL_SCRIPT, coverage);

                writeCoverageToFile(scriptName, new ExternalScriptCoverageReport(coverageMetadata, coverage, coverageMet));

                if (externalScriptMetadata == null) {
                    throw new TestException("External script metadata not found");
                }

                List<String> missedLines = getMissedLines(externalScriptMetadata.totalLines(), coveredLines);
                this.sonarCoverageReporter.addSonarCoverageElement(new SonarScriptCoverageElement(externalScriptMetadata.filePath(), coveredLines, missedLines));
            } catch (Exception e) {
                logger.error("Error encountered in computing coverage for external script: {} :: {}", scriptName, e.getMessage());
            }
        });
    }

    private void handleExternalScriptsWithNoTests(Map<String, ExternalScriptMetadata> externalScripts, List<ScriptRunInstance> scriptRunInstances) {
        externalScripts.forEach((scriptName, metadata) -> {
            boolean hasTest = scriptRunInstances.stream().anyMatch(scriptRun -> scriptName.equals(scriptRun.scriptName()));

            if (!hasTest) {
                CoverageMetadata coverageMetadata = CoverageMetadata.setMetadataForExtScriptCoverage(scriptName);
                try {
                    boolean pass = true;
                    double threshold = coverageMetadata.threshold();
                    if (threshold != 0.0) {
                        pass = false;
                        logCoverageWarning(scriptName, TestResourceType.EXTERNAL_SCRIPT, threshold);
                    }
                    ExternalScriptCoverageReport externalScriptCoverageReport = new ExternalScriptCoverageReport(coverageMetadata, 0.0, pass);
                    writeCoverageToFile(scriptName, externalScriptCoverageReport);
                    SonarScriptCoverageElement sonarScriptCoverageElement = new SonarScriptCoverageElement(metadata.filePath(), Collections.emptySet(), metadata.totalLines());
                    this.sonarCoverageReporter.addSonarCoverageElement(sonarScriptCoverageElement);
                } catch (Exception e) {
                    logger.error("Error encountered in writing report external script with no tests  : {} :: {}", scriptName, e.getMessage());
                }
            }
        });
    }

    private void generateCoverageReportsForProcess(List<ScriptRunInstance> scriptRunInstances) throws IOException {
        List<ModelTestMetadata> modelsTestMetadata = metadataProducer.aggregate(buildPath, srcPath, objectMapper);
        if (modelsTestMetadata.isEmpty()) {
            logger.warn("No bpmn files found in project");
        } else {
            for (ModelTestMetadata modelTestMetadata : modelsTestMetadata) {
                ModelCoverageReport modelCoverageReport;
                if (modelTestMetadata.getError() != null) {
                    modelCoverageReport = ModelCoverageReport.setError(modelTestMetadata.getFileName(), modelTestMetadata.getError());
                    logCoverageWarning(modelTestMetadata.getFileName(), TestResourceType.PROCESS, getThreshold());
                } else {
                    modelCoverageReport = computeModelCoverage(modelTestMetadata, scriptRunInstances);
                }
                writeCoverageToFile(modelCoverageReport.metadata().name(), modelCoverageReport);
            }
        }
    }

    private ModelCoverageReport computeModelCoverage(ModelTestMetadata modelTestMetadata, List<ScriptRunInstance> scriptRunInstances) {
        CoverageMetadata coverageMetadata = CoverageMetadata.setMetadataForModelCoverage(modelTestMetadata.getFileName(), modelTestMetadata.getProcessDefinitionKey());
        List<ScriptCoverage> scriptCoverages = new ArrayList<>();
        ScriptMetadata scriptMetadata = modelTestMetadata.getScriptMetadata();
        double processTestCoverage = computeProcessTestCoverage(modelTestMetadata);
        double totalScriptLinesCoveredInModel = 0;
        for (ScriptMetrics scriptMetric : scriptMetadata.scriptCoverageMetadata()) {
            ScriptCoverage scriptCoverage;
            String activityId = scriptMetric.activityId();
            if (scriptMetric.error() != null) {
                scriptCoverage = ScriptCoverage.setError(activityId, scriptMetric.error());
            } else {
                List<String> totalLines = scriptMetric.lines();
                Set<String> linesCovered = getLinesCoveredInScript(modelTestMetadata, scriptRunInstances, activityId);
                List<String> missedLines = getMissedLines(totalLines, linesCovered);
                double totalLinesInScript = totalLines.size();
                double totalLinesCoveredInScript = linesCovered.size();
                double coverage = calculateCoverage(totalLinesInScript, totalLinesCoveredInScript);
                totalScriptLinesCoveredInModel += totalLinesCoveredInScript;
                scriptCoverage = new ScriptCoverage(activityId, coverage, missedLines, null);
                SonarScriptCoverageElement sonarScriptCoverageElement = new SonarScriptCoverageElement(coverageMetadata.processDefinitionKey(), activityId, scriptMetric.scriptFormat(), linesCovered, missedLines);
                this.sonarCoverageReporter.addSonarCoverageElement(sonarScriptCoverageElement);
            }
            scriptCoverages.add(scriptCoverage);
        }
        double totalScriptCoverage = calculateCoverage(scriptMetadata.totalLines(), totalScriptLinesCoveredInModel);
        boolean coverageMet = isCoverageMet(coverageMetadata, processTestCoverage, totalScriptCoverage);
        return new ModelCoverageReport(coverageMetadata, processTestCoverage, totalScriptCoverage, scriptCoverages, coverageMet, null);
    }

    private boolean isCoverageMet(CoverageMetadata metadata, double processTestCoverage, double totalScriptCoverage) {
        boolean processCoverageMet = checkCoverageForType(metadata, TestResourceType.PROCESS, processTestCoverage);
        boolean scriptCoverageMet = checkCoverageForType(metadata, TestResourceType.INLINE_SCRIPT, totalScriptCoverage);
        return processCoverageMet && scriptCoverageMet;
    }

    private boolean checkCoverageForType(CoverageMetadata coverageMetadata, TestResourceType type, double coverage) {
        double threshold = coverageMetadata.threshold();
        boolean coverageMet = coverageThresholdMet(coverage, threshold);
        if (!coverageMet) {
            boolean ignoreCoverageFailure = getIgnoreCoverageFailure();
            if (!ignoreCoverageFailure) {
                setBuildAsError();
            }
            logCoverageWarning(coverageMetadata.name(), type, threshold);
        }
        return coverageMet;
    }

    private static Set<String> getLinesCoveredInScript(ModelTestMetadata modelTestMetadata, List<ScriptRunInstance> scriptRunInstances, String activityId) {
        Set<String> linesCovered = new HashSet<>();
        scriptRunInstances.stream().filter(scriptRunInstance -> isAssociated(modelTestMetadata.getProcessDefinitionKey(), activityId, scriptRunInstance)).forEach(scriptRun -> linesCovered.addAll(scriptRun.coveredLines()));
        return linesCovered;
    }

    private static List<String> getMissedLines(List<String> totalLines, Set<String> linesCovered) {
        List<String> missedLines = new ArrayList<>(totalLines);
        missedLines.removeAll(linesCovered);
        return missedLines;
    }

    private static boolean isAssociated(String processDefinitionKey, String activityId, ScriptRunInstance scriptRunInstance) {
        return !scriptRunInstance.isExternal() && scriptRunInstance.processDefinitionKey().equals(processDefinitionKey) && scriptRunInstance.activityId().equals(activityId);
    }

    private double computeProcessTestCoverage(ModelTestMetadata modelTestMetadata) {
        ProcessTestMetadata processTestMetadata = modelTestMetadata.getProcessTestMetadata();
        if (processTestMetadata != null) {
            return computeProcessTestCoverage(processTestMetadata);
        }
        return 0.0;
    }

    private double computeProcessTestCoverage(ProcessTestMetadata processTestMetadata) {
        try {
            String fluxnovaCoverageTestClass = processTestMetadata.testClass();
            FluxnovaReport fluxnovaCoverageReport = getFluxnovaCoverageReport(fluxnovaCoverageTestClass);
            return calculateProcessCoverageByFluxnovaReport(processTestMetadata, fluxnovaCoverageReport);
        } catch (Exception e) {
            logger.error("An error was encountered in computing process coverage for {} :: {}", processTestMetadata.processDefinitionKey(), e.getMessage());
            return 0.0;
        }
    }

    private double calculateProcessCoverageByFluxnovaReport(ProcessTestMetadata process, FluxnovaReport fluxnovaReport) {
        String processDefinitionKey = process.processDefinitionKey();
        Metrics metrics = new FluxnovaMetricsExtractor().extract(processDefinitionKey, process.testClass(), fluxnovaReport);
        return getCoverage(metrics);
    }

    private void writeCoverageToFile(String fileName, Object coverageReport) throws IOException {
        String coverageFileName = fileName + ".coverage.json";
        Path reportPath = buildPath.resolve("coverage-collection").resolve("code-coverage").resolve(coverageFileName);
        Files.createDirectories(reportPath.getParent());
        String reportAsJson = objectMapper.writeValueAsString(coverageReport);
        Files.writeString(reportPath, reportAsJson);
    }

    private static double getCoverage(Metrics metrics) {
        double elementsExecutedCount = getElementsExecutedCount(metrics.events());
        double totalElementsCount = metrics.totalElements();
        return calculateCoverage(totalElementsCount, elementsExecutedCount);
    }

    private static double getElementsExecutedCount(List<Event> events) {
        Set<String> executedElements = new HashSet<>();
        for (Event event : events) {
            if (event.type().equals(EVENT_END) || event.type().equals(EVENT_TAKE)) {
                executedElements.add(event.definitionKey());
            }
        }
        return executedElements.size();
    }

    private FluxnovaReport getFluxnovaCoverageReport(String testClass) throws IOException {
        Path fluxnovaCoverageReportPath = Path.of(buildPath.toString(), FLUXNOVA_COVERAGE_DIRECTORY, testClass);
        File fluxnovaCoverageReportPathForClass = fluxnovaCoverageReportPath.toFile();
        FilenameFilter filter = (dir, name) -> name.endsWith(".json");
        File[] fluxnovaCoverageReportJsonFiles = fluxnovaCoverageReportPathForClass.listFiles(filter);
        if (fluxnovaCoverageReportJsonFiles != null && fluxnovaCoverageReportJsonFiles.length > 0) {
            File reportFile = fluxnovaCoverageReportJsonFiles[0];
            return objectMapper.readValue(reportFile, FluxnovaReport.class);
        }
        throw new TestException("Fluxnova process test report file not found for " + testClass);
    }

    private void setBuildAsError() {
        MavenExecutionResult mavenExecutionResult = mavenSession.getResult();
        if (mavenExecutionResult.getExceptions().isEmpty()) {
            TestException testException = new TestException("Coverage not met. To disable build failing due to coverage checks use -DignoreCoverageFailure=true");
            mavenExecutionResult.addException(testException);
        }
    }

    private ExternalScriptMetadata getExternalScriptMetadata(String scriptName, Map<String, ExternalScriptMetadata> externalScripts) {
        return externalScripts.get(scriptName);
    }
}
