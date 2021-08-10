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
        const parts = possibleScenario.nameIdentifier.split("::");
        const label = parts[parts.length - 1];
        return Promise.resolve(
          [
            new TransformationItem(label,
              possibleScenario.nameIdentifier,
              ThemeIcon.Folder,
              TreeItemCollapsibleState.Expanded,
              this.scenarios.scenarios
            )
          ]
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
    public readonly uri: Uri | null,
    readonly icon: ThemeIcon,
    public readonly collapsibleState: TreeItemCollapsibleState
  ) {
    super(label, collapsibleState);
    this.tooltip = `${this.label}`;
    this.resourceUri = uri;
  }

  command = this.uri != null ? { command: "vscode.open", title: "Open", arguments: [this.uri] } : null;
  iconPath = this.icon;

  abstract getChildren(): Thenable<ScenarioViewerItem[]>
}

export class TransformationItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public nameIdentifier: string,
    readonly icon: ThemeIcon,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly model: ShowScenarios.Scenario[]) {
    super(label, null, icon, collapsibleState)

  }

  iconPath = this.icon;
  contextValue = "transformationItem"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    return Promise.resolve(this.model.map(scenario => {
      return new ScenariosNode(scenario.name,
        this.nameIdentifier,
        scenario.name,
        URI.parse(scenario.uri),
        ThemeIcon.Folder,
        TreeItemCollapsibleState.Expanded,
        scenario.active,
        scenario)
    })
    )
  }
}

export class ScenariosNode extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly nameIdentifier: string,
    public readonly scenarioName: string,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly active: boolean,
    readonly model: ShowScenarios.Scenario
  ) {
    super(label, uri, icon, collapsibleState)

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
      [
        new InputsItem(
          "Inputs",
          this.nameIdentifier,
          this.scenarioName,
          ThemeIcon.Folder,
          TreeItemCollapsibleState.Expanded,
          this.model.inputsUri),

        new OutputsItem(
          "Outputs",
          this.nameIdentifier,
          this.scenarioName,
          ThemeIcon.Folder,
          TreeItemCollapsibleState.Expanded,
          this.model.outputsUri
        )
      ]
    )
  }


}

export class InputsItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly nameIdentifier: string,
    public readonly scenarioName: string,
    readonly icon: ThemeIcon,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly sampleInputs: Array<ShowScenarios.SampleInput>) {
    super(label, null, icon, collapsibleState)
  }

  contextValue = "inputs"
  iconPath = this.icon;

  getChildren(): Thenable<ScenarioViewerItem[]> {
    if (this.sampleInputs) {
      return Promise.resolve(this.sampleInputs.map(sampleInput =>
        new InputItem(sampleInput.name,
          this.nameIdentifier,
          this.scenarioName,
          sampleInput.name,
          URI.parse(sampleInput.uri),
          ThemeIcon.File,
          TreeItemCollapsibleState.None))
      )
    } else {
      return Promise.resolve([])
    }
  }
}

export class InputItem extends ScenarioViewerItem {

  constructor(
    public readonly label: string,
    public readonly nameIdentifier: string,
    public readonly scenarioName: string,
    public readonly inputName: String,
    public readonly uri: Uri,
    readonly icon: ThemeIcon,
    public readonly collapsibleState: TreeItemCollapsibleState) {
    super(label, uri, icon, collapsibleState)
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
    public readonly collapsibleState: TreeItemCollapsibleState) {
    super(label, uri, icon, collapsibleState)
    this.resourceUri = uri;
  }

  contextValue = "outputItem"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    return Promise.resolve([])
  }

}

export class OutputsItem extends ScenarioViewerItem {

  constructor(public readonly label: string,
    public readonly nameIdentifier: string,
    public readonly scenarioName: string,
    readonly icon: ThemeIcon,
    public readonly collapsibleState: TreeItemCollapsibleState,
    readonly outputUris: string | null) {
    super(label, null, icon, collapsibleState)
  }

  contextValue = "outputs"

  getChildren(): Thenable<ScenarioViewerItem[]> {
    const newLocal = this.outputUris;
    if (newLocal) {
      return Promise.resolve(
        [new OutputItem(Utils.basename(URI.parse(this.outputUris)), URI.parse(this.outputUris), ThemeIcon.File, TreeItemCollapsibleState.Expanded)]
      )
    } else {
      return Promise.resolve([])
    }
  }

}