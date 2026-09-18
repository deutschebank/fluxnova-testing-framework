package org.finos.fluxnova.bpm.test.scripting

import tools.jackson.databind.ObjectMapper
import org.finos.fluxnova.bpm.test.scripting.coverage.ScriptCoverageImpl
import org.finos.fluxnova.bpm.test.scripting.mocks.ConnectorExtension
import org.finos.fluxnova.bpm.test.scripting.mocks.DelegateExecutionExtension
import org.finos.fluxnova.bpm.test.scripting.mocks.MockDate
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity
import org.finos.fluxnova.bpm.model.bpmn.BpmnModelInstance
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.PolyglotException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.finos.fluxnova.bpm.test.coverage.CoverageProperties.getSkipCoverage

class ScriptTestSpecification extends Specification {

    private static final Logger logger = LoggerFactory.getLogger("script-test-specification");

    private static ScriptEngine scriptEngine
    private static execution
    private ScriptEngineType scriptEngineType
    private bpmnFileName
    private BpmnModelInstance bpmnModelInstance
    private ProcessDefinitionEntity processDefinition

    private static ScriptCoverageImpl coverageObj
    private static connector
    def initializeEngine(ScriptEngineType scriptEngineType) {
        this.scriptEngineType = scriptEngineType
        TimeZone.setDefault(TimeZone.getTimeZone("EST"))
        if (ScriptEngineType.JAVASCRIPT == scriptEngineType) {
            scriptEngine = getJavascriptEngine()
        } else if (ScriptEngineType.GROOVY == scriptEngineType) {
            scriptEngine = new ScriptEngineManager().getEngineByName('groovy')
        } else {
            throw new ScriptTestException("Engine not supported: ${scriptEngineType}")
        }
        configureSpin(scriptEngine, scriptEngineType)
        execution = getMockExecution()
        connector = getMockConnector()
        return scriptEngine
    }

    def getExecution() {
        return execution
    }

    def getConnector() {
        return connector
    }

    def getEngine() {
        return scriptEngine
    }

    def executeExternalScript(String scriptName) {
        def script = ScriptTestUtils.loadResource(scriptName)
        return executeScript(script, scriptName, null, true)
    }

    def executeInlineScript(activityId, bpmnFileName) {
        return executeInlineScript(activityId, bpmnFileName, null)
    }

    def executeInlineScript(activityId, bpmnFileName, eventType) {
        this.bpmnFileName = bpmnFileName
        def modelEntities = ScriptTestUtils.extractModelEntities(activityId, bpmnFileName, eventType)
        this.bpmnModelInstance = modelEntities['instance'] as BpmnModelInstance
        return executeScript(modelEntities['script'], bpmnFileName, activityId, false)
    }

    def mockExecutionGetVariable(variables) {
        variables.each { variable ->
            execution.setVariable(variable.key, variable.value)
        }
    }

    def mockConnectorGetVariable(variables) {
        variables.each { variable ->
            connector.setVariable(variable.key, variable.value)
        }
    }

    def stubVariable(String inputName, variable) {
        scriptEngine.put(inputName, variable)
    }

    def mockExecutionAny(String method, response) {
        execution."$method"(*_) >> response
    }

    def mockExecutionAny(String parentMethod, String childMethod, response) {
        if (parentMethod.equals('getProcessDefinition')) {
            if (!processDefinition) {
                processDefinition = Mock()
            }
            processDefinition."${childMethod}"(*_) >> response
            execution."${parentMethod}"(*_) >> processDefinition
        } else {
            logger.warn("Mocking -- ${parentMethod}() -- not supported");
        }
    }

    def verifyThrownException(closure, exception) {
        try {
            closure.call()
            throw new ScriptTestException('No exception thrown')
        } catch (ScriptException ex) {
            assertException(ex, exception, null)
        }
    }

    def verifyThrownException(closure, exception, message) {
        try {
            closure.call()
            throw new ScriptTestException('No exception thrown')
        } catch (ScriptException ex) {
            assertException(ex, exception, message)
        }
    }

    def mockDate(year, month, day, hour, minutes) {
        def epoch = ScriptTestUtils.getEpoch(year, month, day, hour, minutes)
        setMockDate(epoch)
    }

    def mockDate(year, month, day) {
        def epoch = ScriptTestUtils.getEpoch(year, month, day, null, null)
        setMockDate(epoch)
    }

    def mockDate(hour, minutes) {
        def epoch = ScriptTestUtils.getEpoch(null, null, null, hour, minutes)
        setMockDate(epoch)
    }

    def getActualExecutionVariable(variable) {
        return execution.getVariable(variable)
    }

    def getActualConnectorVariable(variable) {
        return connector.getVariable(variable)
    }

    private def writeCoverageToFile(fileName, totalLines, coveredLines, activityId, boolean isExternal) {
        Map<String, Object> map = new HashMap<>()
        if (isExternal) {
            map.put("scriptName", fileName)
        } else {
            def pdk = ScriptTestUtils.getProcessDefinitionKey(this.bpmnModelInstance)
            map.put("processDefinitionKey", pdk)
            map.put("activityId", activityId)
        }
        map.put("totalLines", totalLines.size())
        map.put("coveredLines", coveredLines)
        map.put("isExternal", isExternal);
        writeFile(map)
    }

    private def executeScript(String script, String fullFilePath, activityId, boolean isExternal) {
        finalizeMockExecution()
        if(!getSkipCoverage()) {
            try {
                if(coverageObj == null){
                    coverageObj = new ScriptCoverageImpl()
                }
                Path reportPath = Paths.get("./target/coverage-collection/code-coverage/")
                Files.createDirectories(reportPath)
                def instrumentationObj = coverageObj.getInstrumentationObj(script, scriptEngineType)
                return evaluateScriptWithCoverage(instrumentationObj, fullFilePath, activityId, isExternal)
            } catch (IllegalArgumentException illAEx) {
                return handleExceptionGracefully(illAEx.getMessage())
            } catch (PolyglotException polyEx){
                return handleExceptionGracefully(polyEx.getMessage())
            }
        } else {
            return evaluateScript(script)
        }
    }

    private def evaluateScriptWithCoverage(instrumentationObj, fullFilePath, activityId, boolean isExternal){
        try {
            def evaluatedScript = evaluateScript(instrumentationObj['instrumentedScript'] as String)
            return computeCoverage(instrumentationObj, evaluatedScript, fullFilePath, activityId, isExternal);
        } catch (Exception e) {
            computeCoverage(instrumentationObj, null, fullFilePath, activityId, isExternal);
            throw e;
        }
    }

    private def computeCoverage(instrumentationObj, evaluatedScript, fullFilePath, activityId, boolean isExternal) {
        def linesToTrack = instrumentationObj['linesToTrack']
        def loggingArray = scriptEngine.get('loggingArray');
        if (linesToTrack && loggingArray) {
            def coveredLines = new HashSet(loggingArray as Collection);
            def fileName = ScriptTestUtils.getFileName(fullFilePath)
            def coveragePercent = coverageObj.generateCoverageReport(linesToTrack, coveredLines, fileName)
            writeCoverageToFile(fileName, linesToTrack, coveredLines, activityId, isExternal)
            return ['evalScript': evaluatedScript, 'coveragePercent': coveragePercent]
        }
        return ['evalScript': evaluatedScript, 'coveragePercent': 0]
    }

    private void setMockDate(epoch) {
        if (ScriptEngineType.GROOVY == scriptEngineType) {
            Date.metaClass.constructor = { -> new MockDate(epoch) }
        } else {
            def mockDateScript = MockDate.getMockDateJSScript(epoch)
            evaluateScript(mockDateScript)
        }
    }

    private def getMockExecution() {
        return Mock(DelegateExecutionExtension) {
            def variables = [:]
            setVariable(_, _) >> { entry ->
                variables[entry[0]] = entry[1]
            }

            getVariable(_) >> { String name ->
                return variables[name]
            }

            hasVariable(_) >> { String name ->
                return variables[name] != null
            }
        }
    }

    protected def getMockConnector() {
        return Mock(ConnectorExtension) {
            def variables = [:]
            setVariable(_, _) >> { entry ->
                variables[entry[0]] = entry[1]
            }
            getVariable(_) >> { String name ->
                return variables[name]
            }
            hasVariable(_) >> { String name ->
                return variables[name] != null
            }
        }
    }

    private void assertException(ScriptException ex, exception, message) {
        def causeInstance
        if (scriptEngineType == ScriptEngineType.GROOVY) {
            causeInstance = ex.cause.cause
        } else {
            causeInstance = ex.cause
        }
        assert causeInstance.getClass() == exception

        if (message != null) {
            assert causeInstance.message == message
        }
    }

    private static def getJavascriptEngine() {
        def engine =
                Engine.newBuilder()
                        .allowExperimentalOptions(true)
                        .option("engine.WarnInterpreterOnly", "false")
                        .option("js.ecmascript-version", "2022")
                        .build()
        def context =
                Context.newBuilder("js")
                        .allowAllAccess(true)
        return GraalJSScriptEngine.create(engine, context)
    }

    private static def writeFile(HashMap<String, Object> map) {
        ObjectMapper mapper = new ObjectMapper()
        def scriptCoverageFileName = UUID.randomUUID().toString().replaceAll('-', '') + ".json"
        def path = Paths.get("./target/coverage-collection/").resolve("scripts").resolve(scriptCoverageFileName)
        Files.createDirectories(path.getParent())
        def jsonOutput = mapper.writeValueAsString(map)
        Files.writeString(path, jsonOutput)
    }

    private static def configureSpin(engine, engineType) {
        def scriptPath = 'script/env/' + (engineType == ScriptEngineType.GROOVY ? 'groovy/spin.groovy' : 'javascript/spin.js')
        def spinScript = this.getClassLoader().getResource(scriptPath)
        engine.eval(spinScript.text)
    }

    private static def finalizeMockExecution() {
        scriptEngine.put('execution', execution)
        scriptEngine.put('connector', connector)
        scriptEngine.put('loggingArray', [])
    }

    private static def evaluateScript(String script) {
        return scriptEngine.eval(script);
    }

    private static def handleExceptionGracefully(String message) {
        println('Exception encountered during code coverage instrumentation. Defaulting to standard test')
        println(message)
        return ['evalScript': message, 'coveragePercent': 0]
    }
}
