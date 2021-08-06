import * as vscode from 'vscode';
import { DebugConfiguration, ExtensionContext, OutputChannel, TextEditor, Uri, WorkspaceFolder } from 'vscode';
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


export function handleCustomMessages(client: LanguageClient, context: ExtensionContext, previewContent: PreviewSystemProvider) {

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

    client.onNotification(PublishDependenciesNotification.type, (dependenciesParam) => {
        pendenciesProvider.dependencies = dependenciesParam.dependencies
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
                    return vscode.window.showTextDocument(document, vscode.ViewColumn.Beside, true)
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

        if (result.success) {
            previewContent.previewContent = result.content
        } else {
            previewContent.previewContent = result.errorMessage
        }

        //Show logs in debugg console
        //Clear old logs
        previewLogs.clear()
        //Only change the focus if there are logs
        previewLogs.appendLine("Preview for " + result.uri + " took " + (result.timeTaken / 1000) + " sec")
        previewLogs.show(true)
        result.logs.forEach((log) => {
            previewLogs.appendLine(log)
        })

    })


    client.onNotification(OpenTextDocument.type, (params) => {
        const documentUri = Uri.parse(params.uri);
        vscode.workspace.openTextDocument(documentUri)
            .then(document => {
                console.log("opening " + documentUri);
                if (
                    params.startLine == null
                 || params.startCharacter == null
                 || params.endLine == null
                 || params.endCharacter == null) {
                    vscode.window.showTextDocument(document);
                } else {
                    const range = new vscode.Range(params.startLine, params.startCharacter, params.endLine, params.endCharacter);
                    vscode.window.showTextDocument(document, {selection: range}); 
                }
            });
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
}

