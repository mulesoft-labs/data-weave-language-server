package org.mule.weave.dsp

trait LauncherConfig {

  /**
    * The port that the launcher should use to specify the new process to listen for debugging
    *
    * @return The port
    */
  def debuggerPort: Int

}

case class RunMappingConfiguration(mayBeMapping: Option[String], scenario: Option[String], debuggerPort: Int) extends LauncherConfig




