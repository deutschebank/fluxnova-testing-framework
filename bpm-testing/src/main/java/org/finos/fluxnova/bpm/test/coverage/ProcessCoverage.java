package org.finos.fluxnova.bpm.test.coverage;

import tools.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.test.TestException;
import org.finos.fluxnova.bpm.test.domain.coverage.ProcessTestMetadata;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.finos.fluxnova.bpm.test.coverage.CoverageProperties.getSkipCoverage;

public class ProcessCoverage {
    private ProcessCoverage() {}

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(ProcessCoverage.class);
    private static final String BUILD_FOLDER = "target/coverage-collection";

    public static void register(String processDefinitionKey, Class<?> caller) {
        if (!getSkipCoverage()) {
            try {
                validateProcessDefinitionKey(processDefinitionKey);
                String uuid = UUID.randomUUID().toString().replace("-", "");
                String metadataFileName = uuid + ".process.metadata.json";
                Path metadataFilePath = Path.of(BUILD_FOLDER, metadataFileName);
                ProcessTestMetadata process = new ProcessTestMetadata(processDefinitionKey, caller.getName());
                String processAsJson = objectMapper.writeValueAsString(process);
                Files.createDirectories(metadataFilePath.getParent());
                Files.writeString(metadataFilePath, processAsJson);
            } catch (Exception e) {
                logger.error("Error occurred in generating coverage metadata file for {} :: {}", processDefinitionKey, e.getMessage());
            }
        }
    }

    private static void validateProcessDefinitionKey(String processDefinitionKey) {
        if (StringUtils.isEmpty(processDefinitionKey)) {
            throw new TestException("Missing process definition key");
        }
    }
}
