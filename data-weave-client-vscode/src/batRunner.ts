import { commands, Disposable, OutputChannel, Uri, window, workspace } from 'vscode'

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

  public registerCommands(): Disposable {
    return commands.registerCommand('bat.runCurrentBatTest.invoked', (fileUri: Uri) => {
      const rootFolder = workspace.workspaceFolders.map((folder, index, arr) => folder)[0]
      workspace.saveAll().then(() => {
        this.outputChannel.show(true)
        this.outputChannel.clear()
        this.outputChannel.appendLine("Init")
        commands.executeCommand('bat.runCurrentBatTest', rootFolder?.uri?.fsPath, fileUri.fsPath).then(value => {
          this.outputChannel.appendLine("Finished")
        })
      })
    });
  }

  dispose(): any {}
}
