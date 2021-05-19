import { NotificationType, RequestType } from "vscode-languageserver-protocol";

export namespace LaunchConfiguration {
  export const type = new NotificationType<
    WeaveConfigurationParams,
    void
  >("weave/workspace/run");

  export interface WeaveConfigurationParams {
    type: string,
    name: string,
    request: string,
    noDebug: boolean,    
    properties: WeaveConfigurationProperty[]
  }

  export interface WeaveConfigurationProperty {
    name: string,
    value: string
  }
}