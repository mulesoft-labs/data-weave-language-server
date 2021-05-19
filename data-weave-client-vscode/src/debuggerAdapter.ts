import { DebugAdapterDescriptor, DebugAdapterDescriptorFactory, DebugAdapterExecutable, DebugAdapterServer, DebugSession, WorkspaceFolder, DebugConfiguration, CancellationToken, ProviderResult, ExtensionContext } from "vscode";
import * as vscode from 'vscode';
import { reverse } from "dns";
import * as path from 'path'


export class DataWeaveRunConfigurationProvider implements vscode.DebugConfigurationProvider {

    /**
     * Massage a debug configuration just before a debug session is being launched,
     * e.g. add all missing attributes to the debug configuration.
     */
    resolveDebugConfiguration(folder: WorkspaceFolder | undefined, config: vscode.DebugConfiguration, token?: CancellationToken): ProviderResult<DebugConfiguration> {

        // if launch.json is missing or empty
        if (!config.type && !config.request && !config.name) {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.languageId === 'data-weave') {
                config.type = 'data-weave';
                config.name = 'Run Data Weave';
                config.request = 'launch';
                config.mainFile = "";
                config.scenario = "";
                config.debuggerPort = 6565
            }
        }
        return config;
    }

}


export class DataWeaveTestingRunConfigurationProvider implements vscode.DebugConfigurationProvider {

    /**
     * Massage a debug configuration just before a debug session is being launched,
     * e.g. add all missing attributes to the debug configuration.
     */
    resolveDebugConfiguration(folder: WorkspaceFolder | undefined, config: vscode.DebugConfiguration, token?: CancellationToken): ProviderResult<DebugConfiguration> {

        // if launch.json is missing or empty
        if (!config.type && !config.request && !config.name) {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.languageId === 'data-weave') {
                config.type = 'data-weave-testing';
                config.name = 'Run Data Weave Tesiging';
                config.request = 'launch';
                config.mainFile = "";
                config.testToRun = "";
                config.debuggerPort = 6565
            }
        }
        return config;
    }

}

export class DataWeaveRunDebugAdapterDescriptorFactory implements DebugAdapterDescriptorFactory {
    public async createDebugAdapterDescriptor(session: DebugSession, executable: DebugAdapterExecutable): Promise<DebugAdapterDescriptor> {
        console.log(session.configuration)

        try {
            console.log(session)
            console.log(executable)
            const debugServerPort = <number>(await vscode.commands.executeCommand("dw.runCommand", session.configuration.type));
            console.log("DataWeave Language Server Started at ", debugServerPort)
            if (debugServerPort && debugServerPort >= 0) {
                return new DebugAdapterServer(debugServerPort);


                
            } else {
                // Information for diagnostic:
                // tslint:disable-next-line:no-console
                console.log("Cannot find a port for debugging session");
            }
        } catch (err) {
            console.log("Unable to Start Debugger Server", err)
            vscode.window.showErrorMessage("Unable to Start Debugger Server", err);
        }
        return executable
    }
}


