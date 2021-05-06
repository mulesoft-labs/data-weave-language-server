import * as vscode from 'vscode';
import { Uri } from 'vscode';
import {LanguageClient} from 'vscode-languageclient'
import { OpenFolder } from './interfaces/openFolder';
import { WeaveInputBox } from './interfaces/weaveInputBox';
import { WeaveQuickPick } from './interfaces/weaveQuickPick';


export function handleCustomMessages(client: LanguageClient) {

    client.onRequest(WeaveInputBox.type, (options, requestToken) => {
        return vscode.window
            .showInputBox(options, requestToken)
            .then(WeaveInputBox.handleInput);
        });


    client.onRequest(WeaveQuickPick.type, (params, requestToken) => {
    return vscode.window
        .showQuickPick(params.items, params, requestToken)
        .then((result) => {
        if (result === undefined) {
            return { cancelled: true };
        } else {
            return { itemId: result.id };
        }
        });
    });

    client.onNotification(OpenFolder.type,(openWindowParams)=>{
        vscode.commands.executeCommand(
            "vscode.openFolder",
            Uri.parse(openWindowParams.uri),
            openWindowParams.openNewWindow
          );
    })
 }