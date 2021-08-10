import { TextEncoder } from 'util';
import * as vscode from 'vscode';
import { ShowPreviewResult } from './interfaces/preview';
import { Utils } from 'vscode-uri'


export default class PreviewSystemProvider implements vscode.FileSystemProvider, vscode.CodeLensProvider {

    public static OUTPUT_FILE_NAME = "Preview Output";
    public static OUTPUT_FILE_URI = vscode.Uri.parse("preview:/" + PreviewSystemProvider.OUTPUT_FILE_NAME);
    private _previewContent: ShowPreviewResult.PreviewResult;

    private emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this.emitter.event;
    public get previewContent(): ShowPreviewResult.PreviewResult {
        return this._previewContent;
    }
    public set previewContent(value: ShowPreviewResult.PreviewResult) {
        this._previewContent = value;
        this.emitter.fire([{ type: vscode.FileChangeType.Changed, uri: PreviewSystemProvider.OUTPUT_FILE_URI }])
    }

    public watch(_uri: vscode.Uri, _options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
        return new vscode.Disposable(() => { });
    }

    public async stat(uri: vscode.Uri): Promise<vscode.FileStat> {
        // Trim the leading zero to meet the format used by JSZip   
        return { type: vscode.FileType.File, ctime: Date.now(), mtime: Date.now(), size: 0 };
    }

    public async readDirectory(uri: vscode.Uri): Promise<[string, vscode.FileType][]> {
        return Promise.resolve([
            [PreviewSystemProvider.OUTPUT_FILE_NAME, vscode.FileType.File]
        ]);
    }

    public async readFile(uri: vscode.Uri): Promise<Uint8Array> {
        console.log(this.previewContent);
        var success = this.previewContent?.success
        var content = ""
        if (success) {
            content = this.previewContent?.content
        } else {
            content = this.previewContent?.errorMessage
        }
        return Promise.resolve(new TextEncoder().encode(content))
    }

    public writeFile(_uri: vscode.Uri, _content: Uint8Array, _options: { create: boolean; overwrite: boolean; }): void | Thenable<void> { }

    public createDirectory(_uri: vscode.Uri): void | Thenable<void> { }

    public delete(_uri: vscode.Uri, _options: { recursive: boolean; }): void | Thenable<void> { }

    public rename(_oldUri: vscode.Uri, _newUri: vscode.Uri, _options: { overwrite: boolean; }): void | Thenable<void> { }

    onDidChangeCodeLenses?: vscode.Event<void>;

    provideCodeLenses(document: vscode.TextDocument, token: vscode.CancellationToken): vscode.ProviderResult<vscode.CodeLens[]> {
        if (document.uri.scheme != "preview" || !this.previewContent.success) {
            return null
        } else {
            var range = new vscode.Range(new vscode.Position(0, 0), new vscode.Position(0, 0))
            var fileUri = vscode.Uri.parse(this.previewContent?.uri);
            var fileName = Utils.basename(fileUri)
            var scenarioUri = vscode.Uri.parse(this.previewContent?.scenarioUri);
            var scenarioFileName = Utils.basename(scenarioUri)
            return [new vscode.CodeLens(range, { title: 'Preview on: ' + fileName, command: "vscode.open", arguments: [fileUri]})]
        }
    }

}