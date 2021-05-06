/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */
'use strict'

import * as fs from 'fs'
import * as path from 'path'
import * as net from 'net'
import * as child_process from 'child_process'

import { workspace, ExtensionContext, commands, window, Uri } from 'vscode'
import { LanguageClient, LanguageClientOptions, StreamInfo } from 'vscode-languageclient'
import { BatRunner } from './batRunner'
import { PassThrough } from 'stream'
import * as vscode from 'vscode';
import { DataWeaveDebugAdapterDescriptorFactory, DataWeaveDebuggerConfigurationProvider } from './debuggerAdapter'
import { findJavaExecutable } from './javaUtils'
import JarFileSystemProvider from './jarFileSystemProvider'
import { handleCustomMessages } from './weaveLanguageClient'
import { ProjectCreation } from './interfaces/project'

export function activate(context: ExtensionContext) {
  console.log('Registering registerDebugAdapterDescriptorFactory')
  context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory('data-weave-debugger', new DataWeaveDebugAdapterDescriptorFactory()));
  console.log('Registering registerDebugConfigurationProvider')
  context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('data-weave-debugger', new DataWeaveDebuggerConfigurationProvider()));

  context.subscriptions.push(vscode.workspace.registerFileSystemProvider('jar', new JarFileSystemProvider(), { isReadonly: true, isCaseSensitive: true }));


  function createServer(): Promise<StreamInfo> {
    return new Promise((resolve, reject) => {
      const server = net.createServer(socket => {
        console.log('[DataWeave] Socket created')

        resolve({
          reader: socket,
          writer: socket,
        })

        socket.on('end', () => console.log('[DataWeave] Disconnected'))
      }).on('error', (err) => { throw err })

      let javaExecutablePath = findJavaExecutable('java')

      // grab a random port.
      server.listen(() => {

        const address = server.address()
        const port = typeof address === 'object' ? address.port : 0
        const storagePath = context.storagePath || context.globalStoragePath
        const weaveJarLocation = path.resolve(context.extensionPath, 'libs', 'data-weave-lang-server-all.jar')
        let logFile = storagePath + '/vscode-data-weave-lang-server.log'
        console.log('[DataWeave] STARTING Data Weave LSP')
        console.log('[DataWeave] Storage path: ' + storagePath)
        console.log('[DataWeave] Jar path: ' + weaveJarLocation)
        console.log('[DataWeave] PORT: ' + port)
        console.log('[DataWeave] Log File: ' + logFile)
        console.log('[DataWeave] Java Location: ' + javaExecutablePath)

        // Start the child java process
        let options = { cwd: workspace.rootPath }
        let args = [
          '-jar',
          '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5010',
          weaveJarLocation,
          port.toString()
        ]

        console.log('[DataWeave] Spawning at port: ' + port)
        const process = child_process.spawn(javaExecutablePath, args, options)

        if (!fs.existsSync(storagePath))
          fs.mkdirSync(storagePath)

        const logStream = fs.createWriteStream(logFile, { flags: 'w' })

        const batRunner = new BatRunner(process)
        batRunner.registerCommands(context)
        const outputChannel = batRunner.getOutputChannel()
        const consoleStream = new PassThrough();

        consoleStream.on("data", (data) => {
          const rawMessage: string = data.toString()
          if (rawMessage.lastIndexOf("[BAT]") !== -1) {
            const printableMessage = rawMessage.replace(new RegExp("\\[BAT\\]", 'g'), '')
            outputChannel.append(printableMessage);
          }
        });

        process.stderr.pipe(logStream)
        process.stdout.pipe(logStream)
        process.stderr.pipe(consoleStream)
        process.stdout.pipe(consoleStream)
        console.log('[DataWeave] LSP Started!')
      })
    })

  }

  // Options to control the language client
  let clientOptions: LanguageClientOptions = {
    // Register the server for plain text documents
    documentSelector: ['data-weave'],
    synchronize: {
      // Synchronize the setting section 'dataWeaveLS' to the server
      configurationSection: 'data-weave',
      // Notify the server about file changes to '.clientrc files contain in the workspace
      fileEvents: workspace.createFileSystemWatcher('**/*.{dwl,raml,xml,yaml}')
    },
    diagnosticCollectionName: 'DataWeave'
  }
  const client = new LanguageClient('data-weave', 'Data Weave Language Server', createServer, clientOptions)
  // Create the language client and start the client.

  const disposableClient = client.start()
  client.onReady().then(() => handleCustomMessages(client));
  // Push the disposable to the context's subscriptions so that the
  // client can be deactivated on extension deactivation
  const dwProjectCreateCommand = 'dw.project.create';

  const dwProjectCreateCommandHandler = () => {
    client.onReady().then(() => client.sendNotification(ProjectCreation.type))
  };

  context.subscriptions.push(vscode.commands.registerCommand(dwProjectCreateCommand, dwProjectCreateCommandHandler));
  context.subscriptions.push(disposableClient)



}

