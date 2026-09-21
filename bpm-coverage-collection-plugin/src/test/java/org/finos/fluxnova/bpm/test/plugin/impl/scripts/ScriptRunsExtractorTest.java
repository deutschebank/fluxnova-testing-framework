package org.finos.fluxnova.bpm.test.plugin.impl.scripts;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ScriptRunInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.finos.fluxnova.bpm.test.plugin.utils.TestHelpers.createFile;
import static org.finos.fluxnova.bpm.test.plugin.utils.TestHelpers.deleteTestCoverageFiles;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ScriptRunsExtractorTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Path buildDir = Path.of("target");

    @InjectMocks
    ScriptRunsExtractor scriptRunsExtractor;

    @AfterEach
    void cleanup() {
        deleteTestCoverageFiles();
    }

    @Test
    void extractAll_returnsInlineScriptRunInstances() throws IOException {
        List<String> coveredLinesFirst = Arrays.asList("1", "2", "3");
        List<String> coveredLinesSecond = Arrays.asList("1", "2", "3", "5");
        createInlineScriptRunInstanceFile("processOne", "activityOne", coveredLinesFirst);
        createInlineScriptRunInstanceFile("processOne", "activityTwo", coveredLinesSecond);
        createInlineScriptRunInstanceFile("processTwo", "activityTwo", coveredLinesFirst);

        List<ScriptRunInstance> scriptRunInstances = scriptRunsExtractor.extractAll(buildDir, objectMapper);
        assertEquals(3, scriptRunInstances.size());

        ScriptRunInstance firstScriptRunInstance = getActualScriptRunInstance("processOne", "activityOne", scriptRunInstances);
        assertNotNull(firstScriptRunInstance);
        assertEquals("activityOne", firstScriptRunInstance.activityId());
        assertEquals("processOne", firstScriptRunInstance.processDefinitionKey());
        assertEquals(coveredLinesFirst, firstScriptRunInstance.coveredLines());
        assertFalse(firstScriptRunInstance.isExternal());
        assertNull(firstScriptRunInstance.scriptName());

        ScriptRunInstance secondScriptRunInstance = getActualScriptRunInstance("processOne", "activityTwo", scriptRunInstances);
        assertNotNull(secondScriptRunInstance);
        assertEquals("activityTwo", secondScriptRunInstance.activityId());
        assertEquals("processOne", secondScriptRunInstance.processDefinitionKey());
        assertEquals(coveredLinesSecond, secondScriptRunInstance.coveredLines());
        assertFalse(secondScriptRunInstance.isExternal());
        assertNull(secondScriptRunInstance.scriptName());

        ScriptRunInstance thirdScriptRunInstance = getActualScriptRunInstance("processTwo", "activityTwo", scriptRunInstances);
        assertNotNull(thirdScriptRunInstance);
        assertEquals("activityTwo", thirdScriptRunInstance.activityId());
        assertEquals("processTwo", thirdScriptRunInstance.processDefinitionKey());
        assertEquals(coveredLinesFirst, thirdScriptRunInstance.coveredLines());
        assertFalse(thirdScriptRunInstance.isExternal());
        assertNull(thirdScriptRunInstance.scriptName());
    }

    @Test
    void extractAll_returnsExternalScriptRunInstances() throws IOException {
        List<String> coveredLines = Arrays.asList("1", "2", "3", "5");
        createExternalScriptRunInstanceFile("test-script.groovy", coveredLines);

        List<ScriptRunInstance> scriptRunInstances = scriptRunsExtractor.extractAll(buildDir, objectMapper);

        ScriptRunInstance externalScriptRunInstance = scriptRunInstances.get(0);
        assertNull(externalScriptRunInstance.activityId());
        assertNull(externalScriptRunInstance.processDefinitionKey());
        assertEquals("test-script.groovy", externalScriptRunInstance.scriptName());
        assertEquals(coveredLines, externalScriptRunInstance.coveredLines());
        assertTrue(externalScriptRunInstance.isExternal());
    }

    @Test
    void extractAll_emptyListIfNoFilesFound() throws IOException {
        Path buildDir = Path.of("test");
        List<ScriptRunInstance> scriptRunInstances = scriptRunsExtractor.extractAll(buildDir, objectMapper);
        assertEquals(0, scriptRunInstances.size());
    }

    private static void createInlineScriptRunInstanceFile(String processDefinitionKey, String activityId, List<String> coveredLines) throws IOException {
        String fileName = processDefinitionKey + activityId + ".json";
        Path fluxnovaReportPath = Path.of("target", "coverage-collection", "scripts", fileName);
        Map<String, Object> map = new HashMap<>();
        map.put("processDefinitionKey", processDefinitionKey);
        map.put("activityId", activityId);
        map.put("coveredLines", coveredLines);
        map.put("isExternal", false);
        createFile(fluxnovaReportPath, objectMapper.writeValueAsString(map));
    }

    private static void createExternalScriptRunInstanceFile(String scriptName, List<String> coveredLines) throws IOException {
        String fileName = scriptName + ".json";
        Path fluxnovaReportPath = Path.of("target", "coverage-collection", "scripts", fileName);
        Map<String, Object> map = new HashMap<>();
        map.put("scriptName", scriptName);
        map.put("coveredLines", coveredLines);
        map.put("isExternal", true);
        createFile(fluxnovaReportPath, objectMapper.writeValueAsString(map));
    }

    private static ScriptRunInstance getActualScriptRunInstance(String processDefinitionKey, String activityId, List<ScriptRunInstance> scriptRunInstances) {
        return scriptRunInstances.stream()
                .filter(scriptRunInstance -> scriptRunInstance.processDefinitionKey().equals(processDefinitionKey) && scriptRunInstance.activityId().equals(activityId))
                .findFirst().orElse(null);
    }

}
