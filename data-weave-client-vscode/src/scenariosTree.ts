import { Event, EventEmitter, ProviderResult, ThemeIcon, TreeDataProvider, TreeItem, TreeItemCollapsibleState, Uri } from "vscode";
import { URI, Utils } from "vscode-uri";
import { ShowScenarios } from "./interfaces/scenarioViewer"


export class WeaveScenarioProvider implements TreeDataProvider<ScenarioViewerItem> {

  private _onDidChangeTreeData: EventEmitter<ScenarioViewerItem> = new EventEmitter<ScenarioViewerItem>();
  readonly onDidChangeTreeData: Event<ScenarioViewerItem> = this._onDidChangeTreeData.event;

  private _scenarios: ShowScenarios.ShowScenariosParam = null;

  public get scenarios(): ShowScenarios.ShowScenariosParam {
    return this._scenarios;
  }

  public set scenarios(value: ShowScenarios.ShowScenariosParam) {
    this._scenarios = value;
    this.refresh()
  }


  private refresh(): void {
    this._onDidChangeTreeData.fire();
  }


  getTreeItem(element: ScenarioViewerItem): ScenarioViewerItem | Thenable<ScenarioViewerItem> {
    return element;
  }

  getChildren(element?: ScenarioViewerItem): ProviderResult<ScenarioViewerItem[]> {
    if (element) {
      return element.getChildren()
    } else {
      const possibleScenario = this.scenarios;
      if (possibleScenario) {
        return Promise.resolve(
          [new TransformationItem(Utils.basename(URI.parse(possibleScenario.transformationUri)), URI.parse(this.scenarios.transformationUri), ThemeIcon.Folder, "vscode.open", TreeItemCollapsibleState.Expanded, this.scenarios.scenarios)]
        );
      } else {
        return Promise.resolve([])
      }
    }
  }

}

export abstract class ScenarioViewerItem extends TreeItem {

  constructor(
    public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState
  ) {
    super(label, collapsibleState);
    this.tooltip = `${this.label}`;
    this.resourceUri = uri;
  }

  command = this.commandId != null ? { command: this.commandId, title: "Open", arguments: [this.uri] } : null;
  iconPath = this.icon;

  abstract getChildren(): Thenable<ScenarioViewerItem[]>
}

export class TransformationItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly model: ShowScenarios.Scenario[]) {
    super(label, uri, icon, commandId, collapsibleState)
    this.resourceUri = uri;
  }

  command = this.commandId != null ? { command: this.commandId, title: "Open", arguments: [this.uri] } : null;
  iconPath = this.icon;
  contextValue = "transformationItem"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    return Promise.resolve(this.model.map(scenario => {
      return new ScenariosNode(scenario.name, URI.parse(scenario.uri), ThemeIcon.Folder, "", TreeItemCollapsibleState.Expanded, scenario.active, scenario)
    })
    )
  }
}

export class ScenariosNode extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly active: boolean,
    readonly model: ShowScenarios.Scenario
  ) {
    super(label, uri, icon, commandId, collapsibleState)
    if (active) {
      this.contextValue = "activeScenario"
      this.iconPath = new ThemeIcon("check")
    } else {
      this.contextValue = "scenario"
    }

    this.resourceUri = uri;
  }


  getChildren(): Thenable<ScenarioViewerItem[]> {
    return Promise.resolve(
      [new InputsItem("Inputs", null, ThemeIcon.Folder, "", TreeItemCollapsibleState.Expanded, this.model.inputsUri),
      new OutputsItem("Outputs", null, ThemeIcon.Folder, "", TreeItemCollapsibleState.Expanded, this.model.outputsUri)]
    )
  }


}

export class InputsItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly inputsUri: Array<string>) {
    super(label, uri, icon, commandId, collapsibleState)
    this.resourceUri = uri;
  }

  contextValue = "inputs"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    const newLocal = this.inputsUri;
    if (newLocal) {
      return Promise.resolve(newLocal.map(uri => new InputItem(Utils.basename(URI.parse(uri)), URI.parse(uri), ThemeIcon.File, "vscode.open", TreeItemCollapsibleState.Expanded)))
    } else {
      return Promise.resolve([])
    }

  }

}

export class InputItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState) {
    super(label, uri, icon, commandId, collapsibleState)
    this.resourceUri = uri;
  }

  contextValue = "inputItem"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    return Promise.resolve([])
  }

}
export class OutputItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState) {
    super(label, uri, icon, commandId, collapsibleState)
    this.resourceUri = uri;
  }

  contextValue = "outputItem"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    return Promise.resolve([])
  }

}

export class OutputsItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    readonly commandId: string | null,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly outputUris: Array<string>) {
    super(label, uri, icon, commandId, collapsibleState)
    this.resourceUri = uri;
  }

  contextValue = "outputs"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    const newLocal = this.outputUris;
    if (newLocal) {
      return Promise.resolve(this.outputUris.map(uri => new InputItem(Utils.basename(URI.parse(uri)), URI.parse(uri), ThemeIcon.File, "vscode.open", TreeItemCollapsibleState.Expanded)))
    } else {
      return Promise.resolve([])
    }
  }

}