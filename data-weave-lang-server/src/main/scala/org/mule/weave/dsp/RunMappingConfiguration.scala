package org.mule.weave.dsp

trait LauncherConfig {

  /**
    * The port that the launcher should use to specify the new process to listen for debugging
    *
    * @return The port
    */
  def debuggerPort: Int

  def buildBefore: Boolean = true

  def testRun: Boolean = false

}

case class RunMappingConfiguration(mayBeMapping: Option[String], scenario: Option[String], override val buildBefore: Boolean, debuggerPort: Int) extends LauncherConfig

case class RunWTFConfiguration(mayBeTests: Option[String], testToRun: Option[String], override val buildBefore: Boolean, debuggerPort: Int, dryRun: Boolean = false, override val testRun: Boolean = true) extends LauncherConfig

case class RunIntegrationTestsConfiguration(module: Boolean, mapping: Boolean, updateResult: Boolean, testToRun: Option[String], debuggerPort: Int) extends LauncherConfig
