import * as vscode from 'vscode';
import { DebugConfiguration, ExtensionContext, OutputChannel, TextEditor, Uri, workspace, WorkspaceFolder } from 'vscode';
import { LanguageClient } from 'vscode-languageclient'
import { OpenFolder } from './interfaces/openFolder';
import { WeaveInputBox } from './interfaces/weaveInputBox';
import { WeaveQuickPick } from './interfaces/weaveQuickPick';
import { showInputBox, showQuickPick } from './widgets';
import { LaunchConfiguration } from './interfaces/configurations';
import { OpenTextDocument } from './interfaces/openTextDocument';
import { ShowPreviewResult } from './interfaces/preview';
import { PublishDependenciesNotification } from './interfaces/dependency';
import { WeaveDependenciesProvider } from './dependencyTree';
import { ClientWeaveCommands, ServerWeaveCommands } from './weaveCommands';
import PreviewSystemProvider from './previewFileSystemProvider';
import { JobEnded, JobStarted } from './interfaces/jobs';
import { InputItem, InputsItem, ScenariosNode, TransformationItem, WeaveScenarioProvider } from './scenariosTree';
import { ShowScenarios } from './interfaces/scenarioViewer';
import { ClearEditorDecorations, SetEditorDecorations } from "./interfaces/editorDecoration";
import { clearDecorations, openTextDocument, setDecorations } from "./document";
import { WeavePublishTests, WeavePushTestResult } from './interfaces/tests';
import { URI, Utils } from 'vscode-uri';


export function handleCustomMessages(client: LanguageClient, context: ExtensionContext, previewContent: PreviewSystemProvider, testController: vscode.TestController) {

    let jobs: { [key: string]: { label: string, description: string } } = {}

    let statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 1)

    client.onNotification(JobStarted.type, (jobId) => {
        jobs[jobId.id] = { label: jobId.label, description: jobId.description }
        statusBar.text = "$(sync~spin) " + jobId.label + " ..."
        statusBar.tooltip = jobId.description
        statusBar.command = ClientWeaveCommands.SHOW_LOG
        statusBar.show()
    });

    client.onNotification(JobEnded.type, (jobId) => {
        delete jobs[jobId.id]
        const remainingJobs = Object.values(jobs);
        if (remainingJobs.length == 0) {
            statusBar.hide()
        } else {
            statusBar.text = "$(sync~spin) " + remainingJobs[0].label + " ..."
            statusBar.tooltip = remainingJobs[0].description
            statusBar.show()
        }
    });

    client.onRequest(WeaveInputBox.type, (options, requestToken) => {
        return showInputBox(options);
    });

    client.onRequest(WeaveQuickPick.type, (options, requestToken) => {
        return showQuickPick(options);
    });

    client.onNotification(SetEditorDecorations.type, (options) => {
        setDecorations(options);
    });

    client.onNotification(ClearEditorDecorations.type, () => {
        clearDecorations();
    });

    client.onNotification(OpenFolder.type, (openWindowParams) => {
        vscode.commands.executeCommand(
            "vscode.openFolder",
            Uri.parse(openWindowParams.uri),
            openWindowParams.openNewWindow
        );
    })

    const previewLogs: OutputChannel = vscode.window.createOutputChannel("DataWeave Preview Logs");
    let languages: string[] = null


    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.ENABLE_PREVIEW, () => {
        if (vscode.window.activeTextEditor) {
            vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                title: "Enable Preview",
                cancellable: true
            }, (progress, token) => {
                progress.report({ increment: 0 });
                const uri = vscode.window.activeTextEditor.document.uri.toString();
                progress.report({ increment: 10 });
                const command = vscode.commands.executeCommand(ServerWeaveCommands.ENABLE_PREVIEW, true, uri);
                return command;
            });
        }
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.RUN_PREVIEW, () => {
        if (vscode.window.activeTextEditor) {
            vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                title: "Running Preview",
                cancellable: true
            }, (progress, token) => {
                progress.report({ increment: 0 });
                const uri = vscode.window.activeTextEditor.document.uri.toString();
                progress.report({ increment: 10 });
                const command = vscode.commands.executeCommand(ServerWeaveCommands.RUN_PREVIEW, uri);
                return command;
            });
        }
    }));


    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.ADD_SCENARIO_COMMAND, (transform: TransformationItem) => {
        vscode.commands.executeCommand(ServerWeaveCommands.CREATE_SCENARIO, transform.nameIdentifier);
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.DELETE_SCENARIO_COMMAND, (scenario: ScenariosNode) => {
        vscode.commands.executeCommand(ServerWeaveCommands.DELETE_SCENARIO, scenario.nameIdentifier, scenario.scenarioName);
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.DEFAULT_SCENARIO_COMMAND, (scenario: ScenariosNode) => {
        vscode.commands.executeCommand(ServerWeaveCommands.DELETE_SCENARIO, scenario.nameIdentifier, scenario.scenarioName);
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.ADD_INPUT_COMMAND, (inputs: InputsItem) => {
        vscode.commands.executeCommand(ServerWeaveCommands.CREATE_INPUT_SAMPLE, inputs.nameIdentifier, inputs.scenarioName);
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.DELETE_INPUT_COMMAND, (input: InputItem) => {
        const inputUri = input.uri.toString();
        vscode.commands.executeCommand(ServerWeaveCommands.DELETE_INPUT_SAMPLE, input.nameIdentifier, input.scenarioName, inputUri);
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.DISABLE_PREVIEW, () => {
        vscode.commands.executeCommand(ServerWeaveCommands.ENABLE_PREVIEW, false);
    }));

    context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.SHOW_LOG, () => {
        client.outputChannel.show(true)
    }));



    let pendenciesProvider = new WeaveDependenciesProvider()

    vscode.window.createTreeView('weaveDependencies', {
        treeDataProvider: pendenciesProvider
    });

    let scenariosProvider = new WeaveScenarioProvider()

    vscode.window.createTreeView('weaveScenarios', {
        treeDataProvider: scenariosProvider
    });

    client.onNotification(PublishDependenciesNotification.type, (dependenciesParam) => {
        pendenciesProvider.dependencies = dependenciesParam.dependencies
    })

    client.onNotification(ShowScenarios.type, async (weaveScenarios) => {
        scenariosProvider.scenarios = weaveScenarios
    })

    client.onNotification(ShowPreviewResult.type, async (result) => {
        const editors = vscode.window.visibleTextEditors;
        let previewEditor = vscode.window.visibleTextEditors.find((editor) => {
            return editor.document.uri == PreviewSystemProvider.OUTPUT_FILE_URI
        })

        if (languages == null) {
            languages = await vscode.languages.getLanguages()
        }
        if (!previewEditor) {
            previewEditor = await vscode.workspace.openTextDocument(PreviewSystemProvider.OUTPUT_FILE_URI)
                .then((document) => {
                    vscode.languages.setTextDocumentLanguage(document, "json");
                    var options = { viewColumn: vscode.ViewColumn.Beside, preserveFocus: true }
                    return vscode.window.showTextDocument(document, options)
                })
        }


        const document = previewEditor.document;
        let languageId = "plaintext"
        if (result.mimeType) {
            const subType = result.mimeType.split("/")[1]
            if (subType == "dw") {
                languageId = "data-weave"
            } else if (languages.includes(subType)) {
                languageId = subType
            }
        }
        if (document.languageId != languageId) {
            vscode.languages.setTextDocumentLanguage(document, languageId);
        }

        previewContent.previewContent = result

        //Show logs in debugg console
        //Clear old logs
        previewLogs.clear()
        if (result.logs.length > 0) {
            previewLogs.show(true)
        }
        //Only change the focus if there are logs
        previewLogs.appendLine("Preview for " + result.uri + " took " + (result.timeTaken / 1000) + " sec")
        result.logs.forEach((log) => {
            previewLogs.appendLine(log)
        })

    })

    client.onNotification(OpenTextDocument.type, (params) => {
        openTextDocument(params.uri);
    })

    client.onNotification(LaunchConfiguration.type, (params) => {
        const additionalProps = params.properties.reduce((acc, cur, i) => {
            acc[cur.name] = cur.value;
            return acc;
        }, {})

        const config: DebugConfiguration = {
            type: params.type,
            name: params.name,
            request: params.request,
            noDebug: params.noDebug,
            ...additionalProps
        }
        const workspaceFolder: WorkspaceFolder = vscode.workspace.workspaceFolders[0];
        vscode.debug.startDebugging(workspaceFolder, config)
    });

    var tests: vscode.TestItem[] = []

    var run: vscode.TestRun

    const runHandler = async (request: vscode.TestRunRequest, cancellation: vscode.CancellationToken) => {
        if (run) {
            run.end()
        }
        var queue = tests
        run = testController.createTestRun(new vscode.TestRunRequest(tests));
        queue.forEach(test => run.enqueued(test))
        var testCounter = 0
        client.onNotification(WeavePushTestResult.type, (params) => {
            const foundItem = queue.find(item => item.label === params.name)
            if (params.event === "testSuiteStarted" || params.event === "testStarted") {
                run.started(foundItem)
                testCounter++
            } else if (params.event === "testSuiteFinished" || params.event === "testFinished") {
                run.passed(foundItem, params.duration)
                queue = queue.filter(item => item != foundItem)
                testCounter--
                if (testCounter == 0) {
                    run.end()
                    run = null
                }
            } else if (params.event === "testFailed") {
                testCounter--
                const failureMessage = new vscode.TestMessage(params.message);
                failureMessage.location = new vscode.Location(foundItem.uri, foundItem.range)
                run.failed(foundItem, failureMessage, params.duration)
                queue = queue.filter(item => item != foundItem)
                if (testCounter == 0) {
                    run.end()
                    run = null
                }
            } else if (params.event === "testStdOut") {
                run.appendOutput(params.message)
                run.appendOutput("\n")
            }
        })
        await vscode.commands.executeCommand(ServerWeaveCommands.LAUNCH_MAPPING, Utils.basename(request.include[0].uri).slice(0, -4), "data-weave-testing")
    }

    testController.createRunProfile('Run Tests', vscode.TestRunProfileKind.Debug, runHandler, true);


    client.onNotification(WeavePublishTests.type, (params) => {
        tests = []
        const itemCollection = testController.items
        itemCollection.forEach(item => testController.items.delete(item.id))
        const siblings = params.rootTestItems
        createTestItem(siblings, itemCollection);
    })

    function createTestItem(siblings: WeavePublishTests.WeaveTestItem[], itemCollection: vscode.TestItemCollection) {
        if (siblings) {
            siblings.forEach(weaveItem => {
                const testItem = testController.createTestItem(weaveItem.id, weaveItem.label, Uri.parse(weaveItem.uri));
                if (weaveItem.range) {
                    var startLine = weaveItem.range.start.line;
                    var startCharacter = weaveItem.range.start.character;
                    if (startCharacter > 0) {
                        startCharacter = startCharacter - 1
                    }
                    if (startLine > 0) {
                        startLine = startLine - 1
                    }
                    const position = new vscode.Position(startLine, startCharacter);
                    testItem.range = new vscode.Range(position, position)
                }
                tests.push(testItem)
                itemCollection.add(testItem);
                createTestItem(weaveItem.children, testItem.children)
            }
            );
        }
    }
}

