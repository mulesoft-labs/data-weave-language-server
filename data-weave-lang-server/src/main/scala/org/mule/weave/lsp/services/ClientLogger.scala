package org.mule.weave.lsp.services

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Handles the logging to the client
 */
class ClientLogger(_client: LanguageClient) {


  def logInfo(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Info, message))
    }
  }

  def logWarning(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Warning, message))
    }
  }

  def logError(message: String): Unit = {
    if (_client != null) {
      _client.logMessage(new MessageParams(MessageType.Error, message))
    }
  }

  def logError(message: String, throwable: Throwable): Unit = {
    if (_client != null) {
      val stringWriter = new StringWriter()
      throwable.printStackTrace(new PrintWriter(stringWriter))
      _client.logMessage(new MessageParams(MessageType.Error, message + " caused by:\n" + stringWriter.toString))
    }
  }


}
