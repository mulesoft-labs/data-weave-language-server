import { commands, Disposable, ExtensionContext, OutputChannel, Uri, window, workspace } from 'vscode'

export class BatRunner implements Disposable {
  private outputChannel: OutputChannel;
  private process
  constructor(_process) {
    this.outputChannel = window.createOutputChannel("BAT_OUTPUT");
    this.process = _process

  }

  public getOutputChannel(): OutputChannel {
    return this.outputChannel
  }

  public registerCommands(context: ExtensionContext): void {
    const runCurrentTestCommand = commands.registerCommand('bat.runCurrentBatTest.invoked', (fileUri: Uri) => {
      const rootFolder = workspace.workspaceFolders.map((folder, index, arr) => folder)[0]
      workspace.saveAll().then(() => {
        this.outputChannel.show(true)
        this.outputChannel.clear()
        commands.executeCommand('bat.runCurrentBatTest', rootFolder?.uri?.fsPath, fileUri.fsPath)
      })
    })
    const runFolderCommand = commands.registerCommand('bat.runFolder.invoked', (fileUri: Uri) => {
      const rootFolder = workspace.workspaceFolders.map((folder, index, arr) => folder)[0]
      workspace.saveAll().then(() => {
        this.outputChannel.show(true)
        this.outputChannel.clear()
        commands.executeCommand('bat.runFolder', rootFolder?.uri?.fsPath)
      })
    })
    context.subscriptions.push(runCurrentTestCommand)
    context.subscriptions.push(runFolderCommand)
  }

  dispose(): any {}
}
