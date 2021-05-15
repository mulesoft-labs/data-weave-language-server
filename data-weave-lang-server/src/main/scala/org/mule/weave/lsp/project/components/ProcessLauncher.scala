package org.mule.weave.lsp.project.components

import org.mule.weave.dsp.JavaExecutableHelper
import org.mule.weave.dsp.LauncherConfig
import org.mule.weave.dsp.RunMappingConfiguration
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.client.WeaveQuickPickItem
import org.mule.weave.lsp.client.WeaveQuickPickParams
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.util
import java.util.concurrent.ExecutionException
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

/**
  * Handles the initialization of the weave process
  */
trait ProcessLauncher {

  type ConfigType <: LauncherConfig

  /**
    * Parses the arguments and build a LauncherConfig for this Launcher
    *
    * @param args The args to be parsed
    * @return The parsed Config
    */
  def parseArgs(args: Map[String, AnyRef]): ConfigType

  /**
    * Launches a process
    *
    * @param config The config taken from the parseArgs
    */
  def launch(config: ConfigType, debugging: Boolean): Option[Process]

}


class DefaultWeaveLauncher(projectKind: ProjectKind,
                           clientLogger: ClientLogger,
                           languageClient: WeaveLanguageClient,
                           vfs: ProjectVirtualFileSystem) extends ProcessLauncher {

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

    var nameIdentifier: Option[String] = None
    val maybeMapping: Option[String] = config.mayBeMapping
    maybeMapping match {
      case Some(mappingNameID) if (mappingNameID.nonEmpty) => {
        nameIdentifier = maybeMapping
      }
      case _ => {
        try {
          val items: Array[WeaveQuickPickItem] = vfs.listFiles().asScala
            .filter((vf) => {
              vf.url().endsWith(".dwl")
            }).map((s) => {
            WeaveQuickPickItem(s.getNameIdentifier.toString, s.getNameIdentifier.toString)
          }).toArray
          val result = languageClient.weaveQuickPick(WeaveQuickPickParams(util.Arrays.asList(items: _*))).get()
          if (!result.cancelled) {
            nameIdentifier = Option(result.itemId)
          }
        } catch {
          case _: InterruptedException | _: ExecutionException => {}
        }
      }
    }
    if (nameIdentifier.isDefined) {
      val theNameIdentifier = NameIdentifier(nameIdentifier.get)
      var scenarioPath: Option[String] = None
      val maybeSampleDataManager = projectKind.sampleDataManager()
      if (maybeSampleDataManager.isDefined) {
        val sampleManager: SampleDataManager = maybeSampleDataManager.get
        config.scenario match {
          case Some(scenario) if (scenario.nonEmpty) => {
            val maybeScenario = sampleManager
              .searchScenarioByName(theNameIdentifier, scenario)
            if (maybeScenario.isEmpty) {
              scenarioPath = askToPickScenario(theNameIdentifier)
            } else {
              scenarioPath = maybeScenario.map(_.file.getAbsolutePath)
            }
          }
          case _ => {
            scenarioPath = askToPickScenario(theNameIdentifier)
          }
        }
        if (scenarioPath.isDefined) {
          args.add("-scenario")
          args.add(scenarioPath.get)
        }
      }
      args.add(nameIdentifier.get)
      builder.command(args)
      clientLogger.logInfo(s"Executing: ${args.asScala.mkString(" ")}")
      Some(builder.start())
    } else {
      None
    }
  }

  private def askToPickScenario(theNameIdentifier: NameIdentifier): Option[String] = {
    val scenarios = projectKind.sampleDataManager().get.listScenarios(theNameIdentifier)
    if (scenarios.nonEmpty) {
      val items: Array[WeaveQuickPickItem] = scenarios.map((s) => {
        WeaveQuickPickItem(s.file.getAbsolutePath, s.name)
      })
      val response = languageClient.weaveQuickPick(WeaveQuickPickParams(util.Arrays.asList(items: _*)))
      val result = response.get()
      if (result.cancelled) {
        None
      } else {
        Some(result.itemId)
      }
    } else {
      None
    }
  }

  override def parseArgs(args: Map[String, AnyRef]): RunMappingConfiguration = {
    RunMappingConfiguration(
      args.get("mapping").map(_.toString),
      args.get("scenario").map(_.toString),
      TcpClientProtocol.DEFAULT_PORT
    )
  }
}

