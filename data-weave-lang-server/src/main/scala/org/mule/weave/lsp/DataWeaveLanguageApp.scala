package org.mule.weave.lsp

import java.net.Socket
import java.util.logging.Level
import java.util.logging.Logger

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient

object DataWeaveLanguageApp extends App {

  val logger = Logger.getLogger(getClass.getName)

  {
    val port = args(0)
    logger.log(Level.INFO,s"Starting Process at ${port}")
    try {
      val socket = new Socket("localhost", port.toInt)
      val in = socket.getInputStream
      val out = socket.getOutputStream
      val server: WeaveLanguageServer = new WeaveLanguageServer
      val launcher: Launcher[LanguageClient] = LSPLauncher.createServerLauncher(server, in, out)
      val client: LanguageClient = launcher.getRemoteProxy
      server.connect(client)
      logger.log(Level.INFO,s"Starting Language Server.")
      launcher.startListening
    } catch {
      case e: Exception => {
        logger.log(Level.SEVERE, "Error while starting WeaveLSP Process ", e)
      }
    }
  }
}
