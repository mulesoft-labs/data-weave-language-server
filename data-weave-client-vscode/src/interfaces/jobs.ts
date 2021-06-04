import { NotificationType, RequestType } from "vscode-languageserver-protocol";

export namespace JobStarted {
  export const type = new NotificationType<
    JobStartedParams,
    void
  >("weave/workspace/notifyJobStarted");

  export interface JobStartedParams {
    id: string,
    label: string,
    description: string
  }  
}

export namespace JobEnded {
  export const type = new NotificationType<
  JobEndedParams,
    void
  >("weave/workspace/notifyJobEnded");

  export interface JobEndedParams {
    id: string
  }  
}