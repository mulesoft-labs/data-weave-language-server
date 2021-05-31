import * as path from 'path';
import * as vscode from 'vscode';
import { Uri } from 'vscode';

import { PublishDependenciesNotification } from './interfaces/dependency';
import { ClientWeaveCommands } from './weaveCommands';

type DependenciesDefinition = PublishDependenciesNotification.DependenciesDefinition;

export class WeaveDependenciesProvider implements vscode.TreeDataProvider<DependencyTreeItem> {

    private _onDidChangeTreeData: vscode.EventEmitter<DependencyTreeItem | undefined> = new vscode.EventEmitter<DependencyTreeItem | undefined>();
    readonly onDidChangeTreeData: vscode.Event<DependencyTreeItem | undefined> = this._onDidChangeTreeData.event;

    private _dependencies: PublishDependenciesNotification.DependenciesDefinition[] = [];

    public get dependencies(): PublishDependenciesNotification.DependenciesDefinition[] {
        return this._dependencies;
    }
    public set dependencies(value: PublishDependenciesNotification.DependenciesDefinition[]) {
        this._dependencies = value;
        this.refresh()
    }


    private refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element: DependencyTreeItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: DependencyTreeItem): Thenable<DependencyTreeItem[]> {
        if (element) {
            return element.getChildren()
        } else {
            return Promise.resolve(
                this.dependencies
                    .map((dep) => {
                        return new DependencyTreeItem(dep.id, this.toJarUri(dep), new vscode.ThemeIcon("library"), null, vscode.TreeItemCollapsibleState.Collapsed)
                    })
            );
        }
    }

    private toJarUri(dep: PublishDependenciesNotification.DependenciesDefinition): Uri {
        const uri = Uri.parse(dep.uri);
        return Uri.parse("jar://" + uri.path + "!")
    }
}

class DependencyTreeItem extends vscode.TreeItem {
    constructor(
        public readonly label: string,
        public readonly uri: Uri,
        readonly icon: vscode.ThemeIcon,
        readonly commandId: string | null,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState
    ) {
        super(label, collapsibleState);
        this.tooltip = `${this.label}`;
    }

    command = this.commandId != null ? { command: this.commandId, title: "Open", arguments: [this.uri] } : null;
    iconPath = this.icon;
    contextValue = 'dependency';


    getChildren(): Thenable<DependencyTreeItem[]> {
        return vscode.workspace.fs.stat(this.uri)
            .then((fileStat) => {
                if (fileStat.type == vscode.FileType.Directory) {
                    return vscode.workspace.fs.readDirectory(this.uri)
                        .then((ls) => {
                            return ls.map((file) => {
                                const fileUri = Uri.parse(this.uri.toString() + "/" + file[0]);
                                const isDirectory = file[1] == vscode.FileType.Directory;
                                const kind = isDirectory ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None;
                                const icon = isDirectory ? vscode.ThemeIcon.Folder : vscode.ThemeIcon.File
                                const commnadId = isDirectory ? null : ClientWeaveCommands.OPEN_FILE 
                                return new DependencyTreeItem(file[0], fileUri, icon, commnadId, kind)
                            })
                        })
                } else {
                    return []
                }
            })

    }
}
