package org.mule.weave.lsp.utils

import org.mule.weave.lsp.services.ClientLogger

import java.io.OutputStream
import java.io.PrintStream
import java.io.IOException

class LoggerOutputStream(clientLogger: ClientLogger) extends OutputStream {

  /**
    * Default number of bytes in the buffer.
    */
  private val DEFAULT_BUFFER_LENGTH = 2048

  /**
    * Indicates stream state.
    */
  private val hasBeenClosed = false

  /**
    * Internal buffer where data is stored.
    */
  private val buf: Array[Byte] = new Array[Byte](DEFAULT_BUFFER_LENGTH)

  /**
    * The number of valid bytes in the buffer.
    */
  private var count = 0


  override def write(b: Int): Unit = {

    if (hasBeenClosed) {
      throw new IOException("The stream has been closed.")
    }
    // don't log nulls
    if (b == 0) return
    // would this be writing past the buffer?
    if (count == buf.length) { // grow the buffer
      flush()
    }
    buf(count) = b.asInstanceOf[Byte]
    count = count + 1
  }

  override def flush(): Unit = {
    if (count > 0) {
      val bytes = new Array[Byte](count)
      System.arraycopy(buf, 0, bytes, 0, count)
      val str = new String(bytes)
      clientLogger.logInfo(str)
      count = 0
    }
  }
}

object LoggerOutputStream {
  def interceptStdOut[T](clientLogger: ClientLogger)(callback: => T): T = {
    val oldOutput = System.out
    val oldErr = System.err
    val printStream = new PrintStream(new LoggerOutputStream(clientLogger))
    try {
      System.setOut(printStream)
      System.setErr(printStream)
      callback
    } finally {
      printStream.flush()
      System.setOut(oldOutput)
      System.setOut(oldErr)
    }
  }
}
