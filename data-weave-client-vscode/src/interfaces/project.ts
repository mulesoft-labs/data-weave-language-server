import { NotificationType } from "vscode-languageserver-protocol";

export namespace ProjectCreation {
    export const type = new NotificationType<
      void
    >("weave/project/create");
    
}