import * as vscode from 'vscode';
import * as JSZip from 'jszip';

//This implementation is based on the ZipFileSystemProvider from
//The url needs to be encoded in the form of
//
export default class JarFileSystemProvider implements vscode.FileSystemProvider {
  private emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
  onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this.emitter.event;

  public watch(_uri: vscode.Uri, _options: { recursive: boolean; excludes: string[]; }): vscode.Disposable {
    return new vscode.Disposable(() => {});
  }


  public async stat(uri: vscode.Uri): Promise<vscode.FileStat> {
    // Trim the leading zero to meet the format used by JSZip
    const entryPath = this.getEntryPath(uri);
    const jarPath = this.getJarPath(uri);
    const archiveUri = vscode.Uri.parse(jarPath);
    const archive = await this.parse(archiveUri);
    const { ctime, mtime, size } = await vscode.workspace.fs.stat(archiveUri);

    

    if (!entryPath) {
      return { type: vscode.FileType.Directory, ctime, mtime, size };
    }

    // Look for directory first (JSZip uses trailing slash) and file second
    const entry = archive.files[entryPath + '/'] || archive.files[entryPath];
    return { type: entry.dir ? vscode.FileType.Directory : vscode.FileType.File, ctime: entry.date.valueOf(), mtime: entry.date.valueOf(), size };
  }

  private getJarPath(uri: vscode.Uri) {
    return uri.path.split("!")[0];
  }

  private getEntryPath(uri: vscode.Uri) {
    const result = uri.path.split("!")[1];
    if(result.startsWith("/"))
      return result.substr(1);
    else
      return result;
  }

  public async readDirectory(uri: vscode.Uri): Promise<[string, vscode.FileType][]> {
    const archiveUri = vscode.Uri.parse(this.getJarPath(uri));
    const archive = await this.parse(archiveUri);
    const entries: [string, vscode.FileType][] = [];

    // Append trailing slash to meet the format used by the archive entries
    const _path = uri.query === '/' ? uri.query : uri.query + '/';
    archive.forEach((path, entry) => {
      // Append leading slash to meet the format used by VS Code API URI
      path = '/' + path;

      // Skip the entry if it is not in the subtree of the scope
      if (!path.startsWith(_path)) {
        return;
      }

      // Cut the path to contextualize it to the current scope
      path = path.slice(_path.length, entry.dir ? -'/'.length : undefined);

      // Do not return self as own entry
      if (!path) {
        return;
      }

      // Skip the entry if it is not immediately within the scope
      if (path.includes('/')) {
        return;
      }

      entries.push([path, entry.dir ? vscode.FileType.Directory : vscode.FileType.File]);
    });

    return entries;
  }

  public createDirectory(_uri: vscode.Uri): void | Thenable<void> {
    // TODO: Report telemetry
    debugger;
  }

  public async readFile(uri: vscode.Uri): Promise<Uint8Array> {
    const archiveUri = vscode.Uri.parse(this.getJarPath(uri));
    const zip = await this.parse(archiveUri);
    const entry = zip.file(this.getEntryPath(uri));
    return entry.async('uint8array');
  }

  public writeFile(_uri: vscode.Uri, _content: Uint8Array, _options: { create: boolean; overwrite: boolean; }): void | Thenable<void> {
    
  }

  public delete(_uri: vscode.Uri, _options: { recursive: boolean; }): void | Thenable<void> {
    
  }

  public rename(_oldUri: vscode.Uri, _newUri: vscode.Uri, _options: { overwrite: boolean; }): void | Thenable<void> {
    
  }

  private async parse(uri: vscode.Uri): Promise<JSZip> {
    const zip = new JSZip();
    await zip.loadAsync(await vscode.workspace.fs.readFile(uri), { createFolders: true });
    return zip;
  }
}