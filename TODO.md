
This document is a log of our discussions thoughts , ideas, reasearches etc so that we can later go back 
and understand our architectural desitions

# Philosophy

Fat Server and Thin Client

FAT SERVER
 - All the Logic goes together on server side

THIN CLIENT
 - UI
 - Broker Services (GIT, LSP ...)
 - Client should implement atomic small operations

MULEDX Platform
  - Service Client Proxy (what can be resolved local it is resolved if not remote)


We should be able to do all the logic on the server and the UI should just be UI components.
For that we are going to:
 - Extend LSP with UI related messages or actions that the server require from the client to do.
 - Add new Protocols for things that are orthogonal to the LSP and are not required by them such as Testing status comunication


# TODO List and links

## Jar Support

- https://github.com/TomasHubelbauer/vscode-zip-file-system

## Dependency management

- https://github.com/shrinkwrap/resolver#resolution-of-artifacts-defined-in-pom-files

- Language client dependencies

## Interactive commands

https://code.visualstudio.com/api/references/extension-guidelines

We need an API for commands to interact with the user through a user interface

The idea is to build LSP extension for this

- Long running actions
- Fatal errors on server
- Request text
- Option selection
- 

### Weave LSP Extentsions

- output/logOutput


### Build View (In New Activity)

- build
- deploy


### Testing View (In New Activity)

https://code.visualstudio.com/api/references/extension-guidelines#view-containers


- New View

    - Define TSP (Test Server Protocol)
    - Study other protocols
    

## Libraries View ( File explorer)

https://code.visualstudio.com/api/references/extension-guidelines#views

 - Icons add library
 - Remove library


## Live view 

Add editor action to enable output

https://code.visualstudio.com/api/references/extension-guidelines#editor-actions


## Discussions


### Quien Calcula el classapth?

#### Option a:

```
 const script = await launchScript(session.configuration);       
 terminal(script)
```

#### Option b:
  - const debugServerPort = await launchScript(session.configuration);
```
  const debugServerPort =  await launchScript(session.configuration);
   [vscode]       <->        [LSP]
    ui/runInterminal(script) <-| //script = java -classpath .... DWRunner -i
     debuggerPort            <-|
```

#### Option c:
- const debugServerPort = await launchScript(session.configuration);

```
  const {port: debugServerPort, script: script} =  await launchScript(session.configuration);
  terminal(script) //script = java -classpath .... DWRunner -i
  return debuggerPort      
```

```
  |VSCODE| <-> :debuggerPort                |DAP| <-> script: port |Process|
   <-{port: debugServerPort, script: script}  |
                                              |Loop Until process starts


```

###  Testing
             
#### Option b:
```
              - const debugServerPort = await launchTest(session.configuration);  
               [vscode]       <->                      [LSP]             
                 {debugPort: debuggerPort, testPort:testingPort}           <-|                             
             
            
```
### Libraries
            

# Links

Known Client Commands

- https://code.visualstudio.com/docs/getstarted/keybindings



# Testing

Client               LSP                              TLP
   |- run   -----> {mode: Test}                           
   |- showView(Test)  <- testStarted(port)             |
   |                 <--------------------- suiteStarted
   |                 <--------------------- testCaseStarted 
