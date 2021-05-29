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


export function handleCustomMessages(client: LanguageClient, context: ExtensionContext) {


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


    context.subscriptions.push(vscode.commands.registerCommand("dw.preview.enable", () => {
        if (vscode.window.activeTextEditor) {
            vscode.commands.executeCommand("dw.enablePreview", true, vscode.window.activeTextEditor.document.uri.toString());
        }
    }));

    context.subscriptions.push(vscode.commands.registerCommand("dw.preview.disable", () => {
        if (vscode.window.activeTextEditor) {
            vscode.commands.executeCommand("dw.enablePreview", false, vscode.window.activeTextEditor.document.uri.toString());
        }
    }));


    client.onNotification(ShowPreviewResult.type, async (result) => {
        var previewUrl: vscode.Uri = vscode.Uri.parse("untitled:" + "/PreviewResult");
        const editors = vscode.window.visibleTextEditors;
        let previewEditor = vscode.window.visibleTextEditors.find((editor) => {
            return editor.document.uri == previewUrl
        })
        if (languages == null) {
            languages = await vscode.languages.getLanguages()
        }
        if (!previewEditor) {
            previewEditor = await vscode.workspace.openTextDocument(previewUrl)
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
            previewEditor.edit((edit) => {
                clearPreviewEditor(edit, previewEditor);
                edit.insert(new vscode.Position(0, 0), result.content)
            })
        } else {
            previewEditor.edit((edit) => {
                clearPreviewEditor(edit, previewEditor);
                edit.insert(new vscode.Position(0, 0), result.errorMessage)
            })
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
                vscode.window.showTextDocument(document);
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

    function clearPreviewEditor(edit: vscode.TextEditorEdit, previewEditor: TextEditor) {
        if (previewEditor.document) {
            var firstLine = previewEditor.document.lineAt(0);
            var lastLine = previewEditor.document.lineAt(previewEditor.document.lineCount - 1);
            var textRange = new vscode.Range(firstLine.range.start, lastLine.range.end);
            edit.delete(textRange);
        }
    }
}

