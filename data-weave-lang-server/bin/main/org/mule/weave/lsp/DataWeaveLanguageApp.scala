package org.mule.weave.lsp

import java.io.IOException
import java.net.Socket

import org.eclipse.lsp4j.launch.LSPLauncher

object DataWeaveLanguageApp extends App {

  {
    val port = args(0)
    try {
      val socket = new Socket("localhost", port.toInt)
      val in = socket.getInputStream
      val out = socket.getOutputStream
      val server = new WeaveLanguageServer
      val launcher = LSPLauncher.createServerLauncher(server, in, out)
      val client = launcher.getRemoteProxy
      server.connect(client)
      launcher.startListening
    } catch {
      case e: IOException => e.printStackTrace()
    }
  }
}
