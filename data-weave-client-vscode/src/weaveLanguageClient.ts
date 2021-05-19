import * as vscode from 'vscode';
import { Uri } from 'vscode';
import { LanguageClient } from 'vscode-languageclient'
import { OpenFolder } from './interfaces/openFolder';
import { WeaveInputBox } from './interfaces/weaveInputBox';
import { WeaveQuickPick } from './interfaces/weaveQuickPick';
import { showInputBox, showQuickPick } from './widgets';


export function handleCustomMessages(client: LanguageClient) {


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
}

