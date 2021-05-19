import * as vscode from 'vscode';
import { DebugConfiguration, Uri, WorkspaceFolder } from 'vscode';
import { LanguageClient } from 'vscode-languageclient'
import { OpenFolder } from './interfaces/openFolder';
import { WeaveInputBox } from './interfaces/weaveInputBox';
import { WeaveQuickPick } from './interfaces/weaveQuickPick';
import { showInputBox, showQuickPick } from './widgets';
import { LaunchConfiguration } from './interfaces/configurations';


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

    client.onNotification(LaunchConfiguration.type, (params) => {

        const additionalProps = params.properties.reduce((acc, cur, i) =>{
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
        const workspaceFolder:WorkspaceFolder = vscode.workspace.workspaceFolders[0];
        vscode.debug.startDebugging(workspaceFolder, config)
    });
}

