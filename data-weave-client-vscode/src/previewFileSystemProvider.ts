import { TextEncoder } from 'util';
import * as vscode from 'vscode';


export default class PreviewSystemProvider implements vscode.FileSystemProvider {

    public static OUTPUT_FILE_NAME = "Preview Output";
    public static OUTPUT_FILE_URI = vscode.Uri.parse("preview:/" + PreviewSystemProvider.OUTPUT_FILE_NAME);
    private _previewContent: string = "";
    
    private emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this.emitter.event;
    public get previewContent(): string {
        return this._previewContent;
    }
    public set previewContent(value: string) {
        this._previewContent = value;
        this.emitter.fire([{type: vscode.FileChangeType.Changed, uri: PreviewSystemProvider.OUTPUT_FILE_URI }])
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
        return Promise.resolve(new TextEncoder().encode(this.previewContent))
    }

    public writeFile(_uri: vscode.Uri, _content: Uint8Array, _options: { create: boolean; overwrite: boolean; }): void | Thenable<void> { }

    public createDirectory(_uri: vscode.Uri): void | Thenable<void> { }

    public delete(_uri: vscode.Uri, _options: { recursive: boolean; }): void | Thenable<void> { }

    public rename(_oldUri: vscode.Uri, _newUri: vscode.Uri, _options: { overwrite: boolean; }): void | Thenable<void> { }


}