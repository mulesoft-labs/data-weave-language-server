package org.mule.weave.lsp.project.components

import org.mule.weave.dsp.JavaExecutableHelper
import org.mule.weave.dsp.LauncherConfig
import org.mule.weave.dsp.RunMappingConfiguration
import org.mule.weave.lsp.client.WeaveInputBoxParams
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol

import java.io.File
import java.util
import java.util.concurrent.ExecutionException

/**
  * Handles the initialization of the weave process
  */
trait ProcessLauncher {

  type ConfigType <: LauncherConfig

  def parseArgs(args: Map[String, AnyRef]): ConfigType

  /**
    * Launches a process
    *
    * @param config The config taken from the parseArgs
    */
  def launch(config: ConfigType, debugging: Boolean): Option[Process]

}


class DefaultWeaveLauncher(projectKind: ProjectKind, languageClient: WeaveLanguageClient) extends ProcessLauncher {

  override type ConfigType = RunMappingConfiguration

  val WEAVE_RUNNER_MAIN_CLASS = "org.mule.weave.v2.runtime.utils.WeaveRunner"

  override def launch(config: RunMappingConfiguration, debugging: Boolean): Option[Process] = {
    //Trigger build before each run
    projectKind.buildManager().build()

    val builder = new ProcessBuilder()
    val javaHome = JavaExecutableHelper.currentJavaHome()
    val javaExec = new File(new File(javaHome, "bin"), "java")
    val args = new util.ArrayList[String]()
    args.add(javaExec.toString)
    //We should take args from the user?
    args.add("-Xms64m")
    args.add("-Xmx2G")
    args.add("-XX:+HeapDumpOnOutOfMemoryError")
    ///
    args.add("-cp")
    val dependencyManager = projectKind.dependencyManager()
    val classpath: String = dependencyManager.dependencies().map((dep) => {
      dep.artifact.getAbsolutePath
    }).mkString(File.pathSeparator)

    val projectStructure = projectKind.structure()
    val sources: String = projectStructure.modules.map((module) => {
      if (module.target.isEmpty) {
        module.target.map((folder) => {
          folder.file.map((file) => {
            file.getAbsolutePath
          }).mkString(File.pathSeparator)
        }).mkString(File.pathSeparator)
      } else {
        module.roots.map((r) => {
          val sources = r.sources.map((s) => {
            s.getAbsolutePath
          }).mkString(File.pathSeparator)
          val resources = r.resources.map((s) => {
            s.getAbsolutePath
          }).mkString(File.pathSeparator)
          sources ++ resources
        }).mkString(File.pathSeparator)
      }
    }).mkString(File.pathSeparator)

    args.add(classpath + File.pathSeparator + sources)
    ///
    args.add(WEAVE_RUNNER_MAIN_CLASS)

    if (debugging) {
      args.add("-debug")
    }

    val mainFile = config.mainFile
    mainFile match {
      case Some(value) if (value.nonEmpty) => args.add(value)
      case _ => {
        try {
          val result = languageClient.weaveInputBox(WeaveInputBoxParams("Specify Main File")).get()
          if (result.cancelled) {
            return None
          } else {
            args.add(result.value)
          }
        } catch {
          case _: InterruptedException | _: ExecutionException => {
            //Return as no main was selected
            return None
          }
        }
      }
    }
    builder.command(args)
    Some(builder.start())
  }

  override def parseArgs(args: Map[String, AnyRef]): RunMappingConfiguration = {
    RunMappingConfiguration(args.get("mapping").map(_.toString), Some(args("scenario").toString), TcpClientProtocol.DEFAULT_PORT)
  }
}

