package org.mule.weave.dsp

import java.io.File

object JavaExecutableHelper {

  def currentJavaHome(): File = {
    new File(System.getProperty("java.home"))
  }

}
