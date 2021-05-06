package org.mule.weave.lsp.utils

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.ShowMessageRequestParams

import scala.collection.JavaConverters.seqAsJavaListConverter

object Messages {

  object NewDwProject {
    def creationFailed(what: String, where: String) =
      new MessageParams(
        MessageType.Error,
        s"Could not create $what in $where"
      )
    def yes = new MessageActionItem("Yes")
    def no = new MessageActionItem("No")
    def newWindowMessage =
      "Do you want to open the new project in a new window?"

    def askForNewWindowParams(): ShowMessageRequestParams = {
      val params = new ShowMessageRequestParams()
      params.setMessage(newWindowMessage)
      params.setType(MessageType.Info)
      params.setActions(
        List(
          yes,
          no
        ).asJava
      )
      params
    }

  }
}
