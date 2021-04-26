import { DebugAdapterDescriptor, DebugAdapterDescriptorFactory, DebugAdapterExecutable, DebugAdapterServer, DebugSession, WorkspaceFolder, DebugConfiguration, CancellationToken, ProviderResult, ExtensionContext } from "vscode";
import * as vscode from 'vscode';
import { reverse } from "dns";
import * as path from 'path'
import { startDebugger } from "./weaveCommands";

export class DataWeaveDebuggerConfigurationProvider implements vscode.DebugConfigurationProvider{

    	/**
	 * Massage a debug configuration just before a debug session is being launched,
	 * e.g. add all missing attributes to the debug configuration.
	 */
	resolveDebugConfiguration(folder: WorkspaceFolder | undefined, config: vscode.DebugConfiguration, token?: CancellationToken): ProviderResult<DebugConfiguration> {
        
		// if launch.json is missing or empty
		if (!config.type && !config.request && !config.name) {
			const editor = vscode.window.activeTextEditor;
			if (editor && editor.document.languageId === 'data-weave') {
				config.type = 'data-weave-debugger';
                config.name = 'Remote Debug Running DW';
                config.request = 'attach';
                config.port = 6565;
                config.hostName = "localhost"
			}
		}
		return config;
	}

}

export class DataWeaveDebugAdapterDescriptorFactory implements DebugAdapterDescriptorFactory {
    public async createDebugAdapterDescriptor(session: DebugSession, executable: DebugAdapterExecutable): Promise<DebugAdapterDescriptor> {                
        console.log(session.configuration)
        let error;
        
        try {
            console.log(session)
            console.log(executable)           
            const debugServerPort = await startDebugger();              
            console.log("DataWeave Language Server Started at ", debugServerPort)                      
            if (debugServerPort) {
                return new DebugAdapterServer(debugServerPort);
            } else {
                // Information for diagnostic:
                // tslint:disable-next-line:no-console
                console.log("Cannot find a port for debugging session");
            }
        } catch (err) {
            console.log("Unable to Start Debugger Server",err)
            error = err;
        }
        vscode.window.showErrorMessage("Unable to Start Debugger Server", error);        
    }   
}


