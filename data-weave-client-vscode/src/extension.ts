/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */
'use strict';

import * as fs from "fs"
import * as path from 'path';
import * as net from 'net';
import * as child_process from "child_process";

import { workspace, Disposable, ExtensionContext } from 'vscode';
import { LanguageClient, LanguageClientOptions, SettingMonitor, StreamInfo } from 'vscode-languageclient';

export function activate(context: ExtensionContext) {

	function createServer(): Promise<StreamInfo> {
		return new Promise((resolve, reject) => {
			const server = net.createServer(socket => {
				console.log("[DataWeave] Socket created")

				resolve({
					reader: socket,
					writer: socket,
				});

				socket.on('end', () => console.log("[DataWeave] Disconnected"))
			}).on('error', (err) => { throw err })

			let javaExecutablePath = findJavaExecutable('java');

			

			// grab a random port.
			server.listen(() => {

				const address = server.address()
				const port = typeof address === 'object' ? address.port : 0
				const storagePath = context.storagePath || context.globalStoragePath
				const weaveJarLocation = path.resolve(context.extensionPath, '..', 'data-weave-lang-server', 'build', 'libs', 'data-weave-lang-server-all.jar');
				let logFile = storagePath + '/vscode-data-weave-lang-server.log';
			

				console.log("[DataWeave] Storage path: " + storagePath)
				console.log("[DataWeave] Jar path: " + weaveJarLocation)
				console.log("[DataWeave] PORT: " + port)
				console.log("[DataWeave] Log File: " + logFile)
				console.log("[DataWeave] Java Location: " + javaExecutablePath)

				// Start the child java process
				let options = { cwd: workspace.rootPath };
				let args = [
					'-jar',
					'-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005',
					weaveJarLocation,					
					port.toString()
				]

				console.log("[DataWeave] Spawning at port: " + port);
				const process = child_process.spawn(javaExecutablePath, args, options)

				if (!fs.existsSync(storagePath))
					fs.mkdirSync(storagePath)

				const logStream = fs.createWriteStream(logFile, { flags: 'w' })

				process.stdout.pipe(logStream)
				process.stderr.pipe(logStream)
			});
		});
	};

	// Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for plain text documents
        documentSelector: ['data-weave'],
        synchronize: {
          // Synchronize the setting section 'dataWeaveLS' to the server
          configurationSection: 'dataWeaveLS',
          // Notify the server about file changes to '.clientrc files contain in the workspace
          fileEvents: workspace.createFileSystemWatcher('**/.dwl')
        },
        diagnosticCollectionName: 'DataWeave'
      }

	// Create the language client and start the client.
	let disposable = new LanguageClient('dataWeaveLS', 'Data Weave Language Server', createServer, clientOptions).start();

	// Push the disposable to the context's subscriptions so that the 
	// client can be deactivated on extension deactivation
	context.subscriptions.push(disposable);

}


// MIT Licensed code from: https://github.com/georgewfraser/vscode-javac
function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);

	// First search each JAVA_HOME bin folder
	if (process.env['JAVA_HOME']) {
		let workspaces = process.env['JAVA_HOME'].split(path.delimiter);
		for (let i = 0; i < workspaces.length; i++) {
			let binpath = path.join(workspaces[i], 'bin', binname);
			if (fs.existsSync(binpath)) {
				return binpath;
			}
		}
	}

	// Then search PATH parts
	if (process.env['PATH']) {
		let pathparts = process.env['PATH'].split(path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = path.join(pathparts[i], binname);
			if (fs.existsSync(binpath)) {
				return binpath;
			}
		}
	}

	// Else return the binary name directly (this will likely always fail downstream) 
	return null;
}

function correctBinname(binname: string) {
	if (process.platform === 'win32')
		return binname + '.exe';
	else
		return binname;
}
