package org.mule.weave.lsp.services

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

class MessageLoggerService extends LanguageClientAware {

  private var _client: LanguageClient = _

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


  override def connect(client: LanguageClient): Unit = {
    this._client = client
  }
}
