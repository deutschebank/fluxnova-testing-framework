package org.finos.fluxnova.bpm.test.plugin.impl;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.domain.coverage.ProcessTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.ModelTestMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ExternalScriptMetadata;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ScriptMetrics;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ScriptMetadata;
import org.finos.fluxnova.bpm.test.plugin.utils.FileType;
import org.finos.fluxnova.bpm.test.scripting.coverage.ScriptCoverageImpl;
import org.finos.fluxnova.bpm.model.bpmn.BpmnModelInstance;
import org.finos.fluxnova.bpm.model.bpmn.instance.Script;
import org.finos.fluxnova.bpm.model.bpmn.instance.ScriptTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.finos.fluxnova.bpm.test.plugin.utils.Constants.COVERAGE_COLLECTION_DIRECTORY;
import static org.finos.fluxnova.bpm.test.plugin.utils.Constants.TEST_CLASSES;
import static org.finos.fluxnova.bpm.test.plugin.utils.Utils.*;
import static org.finos.fluxnova.bpm.test.scripting.ScriptTestUtils.getBpmnModelInstance;
import static org.finos.fluxnova.bpm.test.scripting.ScriptTestUtils.getProcessDefinitionKey;
import static org.finos.fluxnova.bpm.test.scripting.coverage.ScriptCoverageImpl.*;

public class MetadataProducer {

    private static final Logger logger = LoggerFactory.getLogger("metadata-aggregator");

    public List<ModelTestMetadata> aggregate(Path buildDir, Path srcDir, ObjectMapper objectMapper) {
        List<ModelTestMetadata> allModelsTestMetadata = getBpmnModelMetadata(buildDir, srcDir);
        List<ProcessTestMetadata> processTestData = getProcessTestMetadata(buildDir, objectMapper);
        combineMetadata(allModelsTestMetadata, processTestData);
        return allModelsTestMetadata;
    }

    public Map<String, ExternalScriptMetadata> extractAllExternalScripts(Path buildDir) {
        Map<String, ExternalScriptMetadata> externalScripts = new HashMap<>();
        Path path = buildDir.resolve(TEST_CLASSES);
        File parent = path.toFile();
        List<File> collectedFiles = collectFiles(parent, FileType.GROOVY.getType(), FileType.JS.getType());
        collectedFiles.forEach(file -> {
            boolean validFile = true;
            String fileName = file.getName();
            String fileType = fileName.endsWith(FileType.GROOVY.getType()) ? FileType.GROOVY.getType() : FileType.JS.getType();
            String fileContent;
            try {
                fileContent = Files.readString(file.toPath());
                if (fileName.endsWith(".groovy")) {
                    validFile = isNotGroovyClass(fileContent);
                }
                if (validFile) {
                    List<String> executableLines = getExecutableLines(fileType, fileContent);
                    externalScripts.put(fileName, new ExternalScriptMetadata(fileName, file.getPath(), executableLines));
                }
            } catch (Exception e) {
                logger.error("Failed to read file '{}': {}", file.getAbsolutePath(), e.getMessage());
            }
        });
        return externalScripts;
    }

    private static boolean isNotGroovyClass(String fileContent) {
        boolean validFile;
        Pattern pattern = Pattern.compile("class\\s+\\w+\\s+extends\\s+(ScriptTest)?Specification");
        validFile = !pattern.matcher(fileContent).find();
        return validFile;
    }

    private List<ModelTestMetadata> getBpmnModelMetadata(Path buildDir, Path srcDir) {
        List<ModelTestMetadata> modelTestMetadataList = new ArrayList<>();
        Path path = buildDir.resolve(TEST_CLASSES);
        File parent = path.toFile();
        List<File> bpmnFiles = collectFiles(parent, FileType.BPMN.getType());
        for (File bpmnFile : bpmnFiles) {
            ModelTestMetadata modelTestMetadata = new ModelTestMetadata();
            modelTestMetadata.setFileName(bpmnFile.getName());
            try {
                byte[] filesInBytes = Files.readAllBytes(bpmnFile.toPath());
                BpmnModelInstance bpmnModelInstance = getBpmnModelInstance(filesInBytes);
                String processDefinitionKey = getProcessDefinitionKey(bpmnModelInstance);
                Collection<ScriptTask> scriptTasks = bpmnModelInstance.getModelElementsByType(ScriptTask.class);
                ScriptMetadata scriptMetadata = getScriptMetadata(processDefinitionKey, scriptTasks, srcDir);
                modelTestMetadata.setProcessDefinitionKey(processDefinitionKey);
                modelTestMetadata.setScriptMetadata(scriptMetadata);
            } catch (Exception e) {
                modelTestMetadata.setError(e.getMessage());
                logger.error("An error was encountered in gathering metadata for {} :: {}", bpmnFile.getName(), e.getMessage());
            }
            modelTestMetadataList.add(modelTestMetadata);
        }
        return modelTestMetadataList;
    }

    private List<ProcessTestMetadata> getProcessTestMetadata(Path buildDir, ObjectMapper objectMapper) {
        List<ProcessTestMetadata> processTestMetadataList = new ArrayList<>();
        Path metadataFilesLocationPath = Path.of(buildDir.toString(), COVERAGE_COLLECTION_DIRECTORY);
        File metadataFilesDirectory = metadataFilesLocationPath.toFile();
        FilenameFilter filter = (dir, name) -> name.endsWith(".metadata.json");
        File[] processMetadataFiles = metadataFilesDirectory.listFiles(filter);
        if (processMetadataFiles != null) {
            for (File file : processMetadataFiles) {
                ProcessTestMetadata processTestMetadata = null;
                try {
                    processTestMetadata = getProcessMetadata(file, objectMapper);
                    processTestMetadataList.add(processTestMetadata);
                } catch (Exception e) {
                    logger.error("An error was encountered gathering process test metadata for {} :: {}", file.getName(), e.getMessage());
                }
            }
        }
        return processTestMetadataList;
    }

    private static ScriptMetadata getScriptMetadata(String processDefinitionKey, Collection<ScriptTask> scriptTasks, Path srcDir) {
        int totalLines  = 0;
        List<ScriptMetrics> scriptCoverageMetadata = new ArrayList<>();
        for (ScriptTask scriptTask : scriptTasks) {
            String activityId = scriptTask.getId();
            if (isScriptValid(scriptTask)) {
                try {
                    Script script = scriptTask.getScript();
                    String scriptFormat = scriptTask.getScriptFormat().toLowerCase();
                    String scriptText = script.getTextContent();
                    List<String> linesNumbers = getExecutableLines(scriptFormat, scriptText);
                    int linesCount = linesNumbers.size();
                    totalLines += linesCount;
                    scriptCoverageMetadata.add(new ScriptMetrics(activityId, scriptFormat, linesNumbers, null));
                    writeInlineScriptToTempFile(processDefinitionKey, scriptTask, srcDir);
                } catch (Exception e) {
                    scriptCoverageMetadata.add(new ScriptMetrics(activityId, null, null, e.getMessage().trim()));
                    logger.error("Error in gathering metadata for script {} :: {}", activityId, e.getMessage());
                }
            }
        }
        return new ScriptMetadata(totalLines, scriptCoverageMetadata);
    }

    private static List<String> getExecutableLines(String scriptFormat, String scriptText) {
        if (!Objects.equals(scriptText, "")) {
            List<Map<String, ?>> astObject;
            if (Objects.equals(scriptFormat, "groovy")) {
                astObject = getASTGroovy(scriptText);
            } else {
                ScriptCoverageImpl scriptCoverageImpl = new ScriptCoverageImpl();
                astObject = scriptCoverageImpl.getASTJS(scriptText);
            }
            return astObject.stream()
                    .map(line -> line.get("lineNum").toString())
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private static void writeInlineScriptToTempFile(String processDefinitionKey, ScriptTask scriptTask, Path srcDir) throws IOException {
        Script script = scriptTask.getScript();
        String extension = scriptTask.getScriptFormat().equalsIgnoreCase("groovy") ? ".groovy" : ".js";
        String activityId = scriptTask.getId();
        String fileName = processDefinitionKey + "_" + activityId + extension;
        Path scriptFilePath = getGeneratedFilePath(srcDir).resolve(fileName);
        Files.createDirectories(scriptFilePath.getParent());
        Files.writeString(scriptFilePath, script.getTextContent());
    }

    private static ProcessTestMetadata getProcessMetadata(File metadataFile, ObjectMapper objectMapper) throws IOException {
        return objectMapper.readValue(metadataFile, ProcessTestMetadata.class);
    }

    private void combineMetadata(List<ModelTestMetadata> modelTestMetadata, List<ProcessTestMetadata> processTestMetadata) {
        for (ModelTestMetadata metadata : modelTestMetadata) {
            Optional<ProcessTestMetadata> linkedMetadata =
                    processTestMetadata.stream()
                            .filter(process -> process.processDefinitionKey().equals(metadata.getProcessDefinitionKey()))
                            .findFirst();
            linkedMetadata.ifPresent(metadata::setProcessTestMetadata);
        }
    }
}

