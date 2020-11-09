import * as fs from 'fs'
import * as path from 'path'
// MIT Licensed code from: https://github.com/georgewfraser/vscode-javac
export function findJavaExecutable(binname: string) {
    binname = correctBinname(binname)
  
    // First search each JAVA_HOME bin folder
    if (process.env['JAVA_HOME']) {
      let workspaces = process.env['JAVA_HOME'].split(path.delimiter)
      for (let i = 0; i < workspaces.length; i++) {
        let binpath = path.join(workspaces[i], 'bin', binname)
        if (fs.existsSync(binpath)) {
          return binpath
        }
      }
    }
  
    // Then search PATH parts
    if (process.env['PATH']) {
      let pathparts = process.env['PATH'].split(path.delimiter)
      for (let i = 0; i < pathparts.length; i++) {
        let binpath = path.join(pathparts[i], binname)
        if (fs.existsSync(binpath)) {
          return binpath
        }
      }
    }
  
    // Else return the binary name directly (this will likely always fail downstream)
    return null
  }
  
  function correctBinname(binname: string) {
    if (process.platform === 'win32')
      return binname + '.exe'
    else
      return binname
  }