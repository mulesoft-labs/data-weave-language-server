import * as vscode from 'vscode';
``
export async function startDebugger() {
   return  <number> (await vscode.commands.executeCommand("dw.launchDebuggerServerAdapter"));
}