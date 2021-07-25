import { NotificationType } from "vscode-languageserver-protocol";

export namespace ShowScenarios {
  export const type = new NotificationType<
    ShowScenariosParam,
    void
  >("weave/workspace/publishScenarios");

  export interface ShowScenariosParam {
    nameIdentifier: string,
    scenarios: Array<Scenario>
  }

  export interface Scenario {
    active: boolean,
    name: string,
    uri: string,
    inputsUri: Array<SampleInput>,
    outputsUri: string| null
  }

  export interface SampleInput {
    uri: string, 
    name: string
  }
}