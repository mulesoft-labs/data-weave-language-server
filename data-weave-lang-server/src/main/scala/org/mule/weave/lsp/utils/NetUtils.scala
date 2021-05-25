package org.mule.weave.lsp.utils

import java.io.IOException
import java.net.ServerSocket

object NetUtils {

  @throws[IOException]
  def freePort(): Int = {
    try {
      val socket = new ServerSocket(0)
      try {
        socket.getLocalPort
      } finally {
        if (socket != null) {
          socket.close()
        }
      }
    }
  }

}
