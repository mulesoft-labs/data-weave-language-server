/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */
'use strict'

import * as fs from 'fs'
import * as path from 'path'
import * as net from 'net'
import * as child_process from 'child_process'

import { workspace, ExtensionContext, commands, window, Uri, ProgressLocation } from 'vscode'
import { LanguageClient, LanguageClientOptions, StreamInfo, TextDocumentIdentifier } from 'vscode-languageclient'
import { BatRunner } from './batRunner'
import { PassThrough } from 'stream'
import * as vscode from 'vscode';
import { DataWeaveRunConfigurationProvider, DataWeaveRunDebugAdapterDescriptorFactory, DataWeaveTestingRunConfigurationProvider } from './debuggerAdapter'
import { findJavaExecutable } from './javaUtils'
import JarFileSystemProvider from './jarFileSystemProvider'
import { handleCustomMessages } from './weaveLanguageClient'
import { ProjectCreation } from './interfaces/project'
import { ClientWeaveCommands, ServerWeaveCommands } from './weaveCommands'
import PreviewSystemProvider from './previewFileSystemProvider'
import { TextDocument } from './interfaces/textDocument'

export function activate(context: ExtensionContext) {
  //Run Mapping
  context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory('data-weave', new DataWeaveRunDebugAdapterDescriptorFactory()));
  context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('data-weave', new DataWeaveRunConfigurationProvider()));

  //Run Tests
  context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory('data-weave-testing', new DataWeaveRunDebugAdapterDescriptorFactory()));
  context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('data-weave-testing', new DataWeaveTestingRunConfigurationProvider()));

  context.subscriptions.push(vscode.workspace.registerFileSystemProvider('jar', new JarFileSystemProvider(), { isReadonly: true, isCaseSensitive: true }));

  const previewFS = new PreviewSystemProvider()
  context.subscriptions.push(vscode.workspace.registerFileSystemProvider('preview', previewFS, { isReadonly: true, isCaseSensitive: true }));


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
//          '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5050',
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
      fileEvents: workspace.createFileSystemWatcher('**/*.{dwl,raml,xml,yaml,java,properties,json,csv,xlsx,multipart,cobol}')
    },
    diagnosticCollectionName: 'DataWeave'
  }
  const client = new LanguageClient('data-weave', 'Data Weave Language Server', createServer, clientOptions)
  // Create the language client and start the client.
  const disposableClient = client.start()
  client.onReady().then(() => {
    handleCustomMessages(client, context, previewFS)
  });
  // Push the disposable to the context's subscriptions so that the
  // client can be deactivated on extension deactivation
  context.subscriptions.push(disposableClient)


  const dwProjectCreateCommandHandler = () => {
    client.onReady().then(() => client.sendNotification(ProjectCreation.type))
  };

  context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.CREATE_PROJECT, dwProjectCreateCommandHandler));

  context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.CREATE_TEST, () => {
    vscode.commands.executeCommand(ServerWeaveCommands.CREATE_TEST)
  }));

  context.subscriptions.push(vscode.commands.registerCommand(ClientWeaveCommands.OPEN_FILE, (resource) => {
    vscode.window.showTextDocument(resource);
  }));

  context.subscriptions.push(window.onDidChangeActiveTextEditor((editor) => {
    if (editor && editor.document.languageId == "data-weave") {
      client.sendNotification(
        TextDocument.type,
        { textDocumentIdentifier: { uri: editor.document.uri.toString() } });
    }
  }))

}

