import { NotificationType } from "vscode-languageserver-protocol";

export namespace PublishDependenciesNotification {
    export const type = new NotificationType<
        DependenciesParams,
        void
    >("weave/workspace/publishDependencies");


    export interface DependenciesParams {
        dependencies: DependenciesDefinition[]
    }

    export interface DependenciesDefinition {
        uri: string,
        id: string
    }
}

