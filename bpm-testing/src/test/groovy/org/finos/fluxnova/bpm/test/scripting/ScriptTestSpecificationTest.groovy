package org.finos.fluxnova.bpm.test.scripting

import tools.jackson.databind.ObjectMapper
import org.finos.fluxnova.bpm.test.coverage.CoverageProperties
import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory
import org.finos.fluxnova.bpm.engine.delegate.BpmnError
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory

import java.nio.file.Files
import java.nio.file.Path

class ScriptTestSpecificationTest extends ScriptTestSpecification {

    Path filePath = Path.of("target", "coverage-collection", "scripts")
    ObjectMapper objectMapper = new ObjectMapper();

    def 'initializeEngine - happy path'(type, script, expectedResponse) {
        setup: 'initialize engine with type'
            initializeEngine(type)
            mockSkipCoverage(true)
        when: 'when script execution'
            def response = executeExternalScript(script)
        then: 'expected script response returned'
            assert response == expectedResponse
        where:
            type                        || script                       || expectedResponse
            ScriptEngineType.GROOVY     || 'unit/hello-world.groovy'    || 'Hello World from Groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/hello-world.js'        || 'Hello World from Javascript'
    }

    def 'initializeEngine - negative'() {
        when: 'initialize engine with type null'
            initializeEngine(null)
        then: 'ScriptTestException thrown'
            def e = thrown(ScriptTestException)
            assert e.message == 'Engine not supported: null'
    }

    def 'getExecution'() {
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.GROOVY)
        when: 'execution retrieved with variable added'
            def execution = getExecution()
            execution.setVariable('test', 'test')
        then: 'expected variables exists'
            assert execution.hasVariable('test')
            assert !execution.hasVariable('notexisting')
            assert execution.getVariable('test') == 'test'
            assert execution.getVariable('notexisting') == null
    }

    def 'getConnector'() {
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.GROOVY)
        when: 'connector retrieved with variable added'
            def connector = getConnector()
            connector.setVariable('test', 'test')
        then: 'expected variables exists on connector'
            assert connector.hasVariable('test')
            assert !connector.hasVariable('notexisting')
            assert connector.getVariable('test') == 'test'
            assert connector.getVariable('notexisting') == null
    }

    def 'getEngine'(type, expectedClass) {
        setup: 'initialize engine with type'
            initializeEngine(type)
        when: 'engine retrieved'
            def engine = getEngine()
        then: 'correct engine type'
            (engine.getFactory().getClass()).isCase(expectedClass)
        where:
            type                            || expectedClass
            ScriptEngineType.GROOVY         || GroovyScriptEngineFactory
            ScriptEngineType.JAVASCRIPT     || GraalJSEngineFactory
    }

    def 'executeExternalScript - negative'() {
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
        when: 'external script not found'
            executeExternalScript('/non-existent-script.js')
        then: 'assert exception thrown for file not found'
            def e = thrown(ScriptTestException)
            assert e.message == 'Error finding file: '
    }

    def 'executeInlineScript - happy path'(type, activityId, expectedResponse) {
        setup: 'initialize engine by type'
            initializeEngine(type)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript(activityId, 'unit/script-task.bpmn')
        then: 'expected script response returned'
            assert response == expectedResponse
        where:
        type                        || activityId           || expectedResponse
        ScriptEngineType.JAVASCRIPT || 'Activity_1wt5y6k'   || 'Hello World from Javascript'
        ScriptEngineType.GROOVY     || 'Activity_1xt02pa'   || 'Hello World from Groovy'
    }

    def 'executeInlineScript - start event execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('StartEvent_1', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse   || eventType
            'Start event'      || 'start'
            'End event'        || 'end'
    }

    def 'executeInlineScript - service task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_0q12s9o', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse           || eventType
            'Service task start event' || 'start'
            'Service task end event'   || 'end'
    }

    def 'executeInlineScript - send task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_05iyyva', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse         || eventType
            'Send task start event'  || 'start'
            'Send task end event'    || 'end'
    }

    def 'executeInlineScript - receive task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_1qhunr3', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse            || eventType
            'Receive task start event'  || 'start'
            'Receive task end event'    || 'end'
    }

    def 'executeInlineScript - manual task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_1b90hmz', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse           || eventType
            'Manual task start event'  || 'start'
            'Manual task end event'    || 'end'
    }

    def 'executeInlineScript - business rule task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_0fqx0ij', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse                  || eventType
            'Business rule task start event'  || 'start'
            'Business rule task end event'    || 'end'
    }

    def 'executeInlineScript - script task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_1pwfz5a', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse          || eventType
            'Script task start event' || 'start'
            'Script task end event'   || 'end'
    }

    def 'executeInlineScript - call activity execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_08jr4i9', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse          || eventType
            'Call activity start event' || 'start'
            'Call activity end event'   || 'end'
    }

    def 'executeInlineScript - sub process execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_0hfstwy', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse          || eventType
            'Sub process start event' || 'start'
            'Sub process end event'   || 'end'
    }

    def 'executeInlineScript - user task execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Activity_1ih12hv', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse        || eventType
            'User task start event' || 'start'
            'User task end event'   || 'end'
    }

    def 'executeInlineScript - end event execution listener with start and end event'(expectedResponse, eventType) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(true)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript('Event_1sw7cmm', 'unit/execution-listeners.bpmn', eventType)
        then: 'Expected script response returned'
            assert response == expectedResponse
        where:
            expectedResponse   || eventType
            'Start event'      || 'start'
            'End event'        || 'end'
    }

    def 'executeInlineScript - negative - invalid event passed into eventType '(activityId) {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
        when: 'inline script retrieved and executed'
            def response = executeInlineScript(activityId, 'unit/execution-listeners.bpmn', "startt")
            then: 'expected exception thrown'
        def e = thrown(ScriptTestException)
            assert e.message == 'Error finding script in model: ' + 'unit/execution-listeners.bpmn' + ' for activityId: ' + activityId
            assert e.cause.message == "Listener script with event type startt not found"
        where:
            activityId          ||_
            'Event_1sw7cmm'     ||_
            'Activity_10mctyn'  ||_
    }

    def 'executeInlineScript - negative'(bpmn) {
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
        when: 'issue retrieving script task in bpmn'
            executeInlineScript('activityId', bpmn)
        then: 'expected exception thrown'
            def e = thrown(ScriptTestException)
            assert e.message == 'Error finding script in model: ' + bpmn + ' for activityId: activityId'
        where:
            bpmn                       || description
            'unit/script-task.bpmn'    || 'Script task with acivity id doesn`t exist'
            'unit/not-exists.bpmn'     || 'bpmn doesn`t exist'
            'unit/no-script-task.bpmn' || 'No script task exists in bpmn'
    }


    def 'mockExecutionGetVariable, mockExecutionAny, stubVariable'(type, script) {
        setup: 'initialize engine and mock and stub variables'
            initializeEngine(type)
            mockSkipCoverage(true)
            def variables = ['first': 'Hello', 'second': 'World!']
            mockExecutionGetVariable(variables)
            stubVariable('segment', 'Hello World!')
            mockExecutionAny('getProcessInstanceId', 'process_instance_id')
        when: 'execute script'
            def response = executeExternalScript(script)
        then: 'variables mocked and stubbed as expected'
            assert response == 'Hello World!'
            1 * execution.setVariable('result', 'Hello World!')
            1 * execution.setVariable('process', 'process_instance_id')
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/hello-world-variables.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/hello-world-variables.js'
    }

    def 'mockConnectorGetVariable - returns mocked value for key'(type, script) {
        setup: 'initialize engine and mock connector variable myKey'
            initializeEngine(type)
            def variables = ['myKey': 'expectedValue']
            mockConnectorGetVariable(variables)
        when: 'execute script'
            executeExternalScript(script)
        then: 'script sets output variable on execution'
            1 * execution.setVariable("output", "expectedValue")
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/connector-variables.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/connector-variables.js'
    }

    def 'mockExecutionAny - parent child method call'(type, script) {
        setup: 'initialize engine and mock and stub variables'
            initializeEngine(type)
            mockExecutionAny('getProcessDefinition', 'getName', 'MyProcessDefinitionName')
            mockExecutionAny('getProcessDefinition', 'getKey', 'MyProcessDefinitionKey')
        when: 'execute script'
            executeExternalScript(script)
        then: 'variables mocked as expected'
            1 * execution.setVariable('name', 'MyProcessDefinitionName')
            1 * execution.setVariable('key', 'MyProcessDefinitionKey')
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/mock-get-process-definition.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/mock-get-process-definition.js'
    }

    def 'mockExecutionAny - unsupported parent child method call'() {
        setup: 'initialize engine and mock and stub variables'
            initializeEngine(ScriptEngineType.GROOVY)
        when: 'execute script'
            mockExecutionAny('getUnsupportedMethod', 'getName', 'MyProcessDefinitionName')
        then: 'variables mocked as expected'
            noExceptionThrown()
    }


    def 'getActualExecutionVariable'(type, script) {
        setup: 'initialize engine'
            initializeEngine(type)
        when: 'execute script and get execution variable'
            executeExternalScript(script)
            def executionVariable = getActualExecutionVariable('test')
        then: 'execution variable retrieved and has expected value'
            assert 'test variable' == executionVariable
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/get-actual-variable.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/get-actual-variable.js'
    }

    def 'getActualConnectorVariable'(type, script) {
        setup: 'initialize engine'
            initializeEngine(type)
        when: 'script using connector.getVariable and setVariable is executed'
            executeExternalScript(script)
            def executionVariable = getActualConnectorVariable('foo')
        then: 'mocked connector returns expected value'
            assert 'bar' == executionVariable
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/connector-mock-test.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/connector-mock-test.js'
    }


    def 'verifyThrownException with message - happy path'(type, script) {
        setup: 'initialize engine'
            initializeEngine(type)
        when: 'execute script'
            verifyThrownException({
                executeExternalScript(script)
            }, BpmnError.class, 'Script Error')
        then: 'exception thrown'
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/exception-thrown.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/exception-thrown.js'
    }

    def 'verifyThrownException with message - negative'(type, script) {
        setup: 'initialize engine'
            initializeEngine(type)
        when: 'execute script'
            verifyThrownException({
                executeExternalScript(script)
            }, BpmnError.class, 'Script Error')
        then: 'no exception thrown in script'
            def e = thrown(ScriptTestException)
            assert e.message == 'No exception thrown'
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/hello-world.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/hello-world.js'
    }

    def 'verifyThrownException without message - happy path'(type, script) {
        setup: 'initialize engine'
            initializeEngine(type)
        when: 'execute script'
            verifyThrownException({
                executeExternalScript(script)
            }, BpmnError.class)
        then: 'exception thrown'
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/exception-thrown.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/exception-thrown.js'
    }

    def 'verifyThrownException without message - negative'(type, script) {
        setup: 'initialize engine'
            initializeEngine(type)
        when: 'execute script'
            verifyThrownException({
                executeExternalScript(script)
            }, BpmnError.class)
        then: 'no exception thrown in script'
            def e = thrown(ScriptTestException)
            assert e.message == 'No exception thrown'
        where:
            type                        || script
            ScriptEngineType.GROOVY     || 'unit/hello-world.groovy'
            ScriptEngineType.JAVASCRIPT || 'unit/hello-world.js'
    }


    def 'mockDate - year month day hours minutes'(type, script, expectedDate) {
        setup: 'initialize engine and mock date'
            initializeEngine(type)
            mockSkipCoverage(true)
            mockDate('2023', '12', '03', '06', '30')
        when: 'execute script'
            def dateResponse = executeExternalScript(script)
        then: 'date mocked as expected'
            assert expectedDate == dateResponse
        where:
            type                        || script                   ||  expectedDate
            ScriptEngineType.GROOVY     || 'unit/print-date.groovy' ||  '03-12-2023 06:30:00'
            ScriptEngineType.JAVASCRIPT || 'unit/print-date.js'     ||  'Sun Dec 03 2023 06:30:00 GMT-0500 (GMT-5)'
    }

    def 'mockDate - year month day'(type, script, expectedDate) {
        setup: 'initialize engine and mock date'
            initializeEngine(type)
            mockSkipCoverage(true)
            mockDate('2023', '12', '03')
        when: 'execute script'
            def dateResponse = executeExternalScript(script)
        then: 'date mocked as expected'
            assert dateResponse.contains(expectedDate)
        where:
            type                        || script                   || expectedDate
            ScriptEngineType.GROOVY     || 'unit/print-date.groovy' || '03-12-2023'
            ScriptEngineType.JAVASCRIPT || 'unit/print-date.js'     || 'Sun Dec 03 2023'
    }

    def 'mockDate - hours minutes'(type, script, expectedDate) {
        setup: 'initialize engine and mock date'
            initializeEngine(type)
            mockSkipCoverage(true)
            mockDate('17', '45')
        when: 'execute script'
            def dateResponse = executeExternalScript(script)
        then: 'date mocked as expected'
            assert dateResponse.contains(expectedDate)
        where:
            type                        || script                   || expectedDate
            ScriptEngineType.GROOVY     || 'unit/print-date.groovy' || '17:45:00'
            ScriptEngineType.JAVASCRIPT || 'unit/print-date.js'     || '17:45:00 GMT-0500 (GMT-5)'
    }
    def 'Code Coverage Groovy - statement types 1'(percent, indexVar, scriptName, totalLines, linesCovered){
        setup: 'initialize engine'
            deleteCoverageReports()
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            execution.setVariable('indexCounter', indexVar)
            execution.setVariable('codeBlockCondition', codeBlockCondition)
            def coverageResult = executeExternalScript('codeCoverage/Statements.groovy')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == percent
        and: 'Coverage file created'
            def coverageFile = getCoverageFileContents()
            assert coverageFile['scriptName'] == scriptName
            assert coverageFile['totalLines'] == totalLines
            assert coverageFile['coveredLines'].size() == linesCovered
        where:
            percent     || indexVar || codeBlockCondition || scriptName         || totalLines   || linesCovered
            84          || 5        || true               || 'Statements.groovy'|| 52           || 44
            80          || 0        || false              || 'Statements.groovy'|| 52           || 42
    }

    def 'Code Coverage Groovy - loop types 1'(percent, indexVar){
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            execution.setVariable('indexCounter', indexVar)
            def coverageResult = executeExternalScript('codeCoverage/Loops.groovy')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == percent
        where:
            percent     || indexVar
            84          || 10
            100         || 0
    }

    def 'Code Coverage JavaScript - statement types 1'(percent, indexVar, scriptName, totalLines, linesCovered){
        setup: 'initialize engine'
            deleteCoverageReports()
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            execution.setVariable('indexCounter', indexVar)
            def coverageResult = executeExternalScript('codeCoverage/Statements.js')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == percent
        and: 'Coverage file created'
            def coverageFile = getCoverageFileContents()
            assert coverageFile['scriptName'] == scriptName
            assert coverageFile['totalLines'] == totalLines
            assert coverageFile['coveredLines'].size() == linesCovered
        where:
            type                         || script                       ||  percent  || indexVar  || scriptName       || totalLines || linesCovered
            ScriptEngineType.JAVASCRIPT  || 'codeCoverage/Statements.js' ||  89       || 10        ||'Statements.js'   || 65         || 58
            ScriptEngineType.JAVASCRIPT  || 'codeCoverage/Statements.js' ||  83       || 6         ||'Statements.js'   || 65         || 54
    }

    def 'Code Coverage JavaScript - loop types 1'(percent, indexVar){
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            execution.setVariable('indexCounter', indexVar)
            def coverageResult = executeExternalScript('codeCoverage/Loops.js')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == percent
        where:
            percent || indexVar
            89      || 10
            100     || 0
    }

    def 'Code Coverage JavaScript - ArrowFunction'() {
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(false)
        when: 'execute script'
            def coverageResult = executeExternalScript('codeCoverage/callbackTest.js')
        then: 'Coverage values is as expected'
            assert coverageResult.coveragePercent == 90
    }

    def 'Code Coverage : closure test'(){
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            def coverageResult = executeExternalScript('codeCoverage/closuretest.groovy')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == 93
    }

    def 'Code Coverage : coe-attempt 2'(percent, jsonString){
        setup: 'initialize engine'
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            execution.setVariable('connectResponse', jsonString)
            execution.setVariable('contextMap', [cardinal:[beneHub:[api:[host:'hello world']]]])
            def publishDocumentsRequest = [
                    userID : 'userId',
                    accountOwnerID : 'accountOwnerID',
                    role : 'role',
                    userFirstName: 'userFirstName',
                    userLastName: 'userLastName',
                    userEmailAddress: 'userEmailAddress',
                    relationSSN: 'relationSSN',
                    documentsUploaded: 'documentsUploaded',
                    publishTo: 'publishTo',
                    transactionTrackingID: 'transactionTrackingID'
            ]
            execution.setVariable('publishDocumentsRequest', publishDocumentsRequest)
            def coverageResult = executeExternalScript('codeCoverage/coe-test2.groovy')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == percent
        where:
            percent     || jsonString
            100         || '{"workitemNumber": 24601}'
            94          || '{"dummyVar": 24601}'
    }

    def 'executeExternalScript - js Object function test'() {
        setup: 'initialize engine by type'
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(false)
        when: 'execute script'
            def execution = getExecution()
            def coverageResult = executeExternalScript('codeCoverage/JSobjects.js')
        then: 'Coverage value is as expected'
            assert coverageResult.coveragePercent == 100
    }

    def 'executeInlineScript - code coverage packaging'(type, activityId, totalLines, linesCovered) {
        setup: 'initialize engine by type'
            deleteCoverageReports()
            initializeEngine(type)
            mockSkipCoverage(false)
        when: 'inline script retrieved and executed'
            executeInlineScript(activityId, 'unit/script-task.bpmn')
        then: 'expected script response returned'
            def fileContent = getCoverageFileContents();
            assert fileContent['activityId'] == activityId
            assert fileContent['processDefinitionKey'] == 'Process_1k7woqc'
            assert fileContent['coveredLines'].size() == linesCovered
            assert fileContent['totalLines'] == totalLines
        where:
            type                        || activityId           || totalLines || linesCovered
            ScriptEngineType.GROOVY     || 'Activity_1xt02pa'   || 1          || 1
            ScriptEngineType.JAVASCRIPT || 'Activity_1wt5y6k'   || 2          || 2
    }

    def 'executeInlineScript - code coverage packaging - exception handling'(type, activityId) {
        setup: 'initialize engine by type'
            deleteCoverageReports()
            initializeEngine(type)
            mockSkipCoverage(false)
        when: 'inline script retrieved and executed'
            verifyThrownException({
                executeInlineScript(activityId, 'unit/error-script-task.bpmn')
            }, BpmnError.class)
        then: 'expected script response returned'
            def fileContent = getCoverageFileContents();
            assert fileContent['activityId'] == activityId
            assert fileContent['processDefinitionKey'] == 'Process_04oz2gp'
            assert fileContent['coveredLines'].size() == 4
            assert fileContent['totalLines'] == 5
        where:
        type                            || activityId
            ScriptEngineType.GROOVY     || 'error_groovy'
            ScriptEngineType.JAVASCRIPT || 'error_js'
    }

    def 'executeInlineScript - code coverage packaging - execution listener script -js'(activityId, eventType){
        setup: 'initialize engine'
            deleteCoverageReports()
            initializeEngine(ScriptEngineType.JAVASCRIPT)
            mockSkipCoverage(false)
        when: 'execute script'
            executeInlineScript(activityId, 'unit/execution-listeners.bpmn', eventType)
        then: 'Coverage value is as expected'
            def fileContent = getCoverageFileContents();
            assert fileContent['activityId'] == activityId
            assert fileContent['processDefinitionKey'] == 'Process_19m1ds6'
        where:
            activityId         || eventType
            'StartEvent_1'     || 'start'
            'StartEvent_1'     || 'end'
    }

    def 'executeInlineScript - code coverage packaging - execution listener script -groovy'(activityId, eventType){
        setup: 'initialize engine'
            deleteCoverageReports()
            initializeEngine(ScriptEngineType.GROOVY)
            mockSkipCoverage(false)
        when: 'execute script'
            executeInlineScript(activityId, 'unit/execution-listeners.bpmn', eventType)
        then: 'Coverage value is as expected'
            def fileContent = getCoverageFileContents();
            assert fileContent['activityId'] == activityId
            assert fileContent['processDefinitionKey'] == 'Process_19m1ds6'
        where:
            activityId             || eventType
            'Activity_0q12s9o'     || 'start'
            'Activity_0q12s9o'     || 'end'
    }

    def deleteCoverageReports() {
        if (filePath.toFile().size() != 0) {
            Files.list(filePath)
                    .filter {f -> f.toString().endsWith(".json")}
                    .forEach {fileToDelete -> {
                        Files.deleteIfExists(fileToDelete);
                    }}
        }
    }

    def getCoverageFileContents() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".json");
        File file = filePath.toFile().listFiles(filter)[0]
        return objectMapper.readValue(file, Map.class);
    }

    def mockSkipCoverage(skip) {
        SpyStatic(CoverageProperties.class)
        CoverageProperties.getSkipCoverage() >> { return skip }
    }
}
