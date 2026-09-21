package org.finos.fluxnova.bpm.test.plugin.impl.scripts;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.plugin.domain.script.ScriptRunInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.finos.fluxnova.bpm.test.plugin.utils.Constants.COVERAGE_COLLECTION_DIRECTORY;

public class ScriptRunsExtractor {

    public List<ScriptRunInstance> extractAll(Path buildPath, ObjectMapper objectMapper) throws IOException  {
        List<ScriptRunInstance> scriptRunInstances = new ArrayList<>();
        Path scriptCoveragesPath = Path.of(buildPath.toString(), COVERAGE_COLLECTION_DIRECTORY, "scripts");
        File scriptCoverages = scriptCoveragesPath.toFile();
        File[] scriptCoveragesFiles  = scriptCoverages.listFiles();
        if (scriptCoveragesFiles != null) {
            for (File scriptRun : scriptCoveragesFiles) {
                ScriptRunInstance scriptRunInstance = objectMapper.readValue(scriptRun, ScriptRunInstance.class);
                scriptRunInstances.add(scriptRunInstance);
            }
        }
        return scriptRunInstances;
    }
}
