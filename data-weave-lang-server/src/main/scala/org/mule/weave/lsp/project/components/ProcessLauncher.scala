package org.mule.weave.lsp.project.components

import org.mule.weave.dsp.JavaExecutableHelper
import org.mule.weave.dsp.LauncherConfig
import org.mule.weave.dsp.RunMappingConfiguration
import org.mule.weave.dsp.RunWTFConfiguration
import org.mule.weave.lsp.client.LaunchConfiguration
import org.mule.weave.lsp.client.LaunchConfiguration.DATA_WEAVE_CONFIG_TYPE_NAME
import org.mule.weave.lsp.client.LaunchConfiguration.WITF_CONFIG_TYPE_NAME
import org.mule.weave.lsp.client.LaunchConfiguration.WTF_CONFIG_TYPE_NAME
import org.mule.weave.lsp.client.WeaveLanguageClient
import org.mule.weave.lsp.client.WeaveQuickPickItem
import org.mule.weave.lsp.client.WeaveQuickPickParams
import org.mule.weave.lsp.project.ProjectKind
import org.mule.weave.lsp.project.components.JavaWeaveLauncher.buildJavaProcessBaseArgs
import org.mule.weave.lsp.services.ClientLogger
import org.mule.weave.lsp.utils.Icons
import org.mule.weave.lsp.utils.URLUtils
import org.mule.weave.lsp.vfs.ProjectVirtualFileSystem
import org.mule.weave.v2.debugger.client.tcp.TcpClientProtocol
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.util
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

object ProcessLauncher {
  def createLauncherByType(configType: String, projectKind: ProjectKind,
                           clientLogger: ClientLogger,
                           languageClient: WeaveLanguageClient,
                           vfs: ProjectVirtualFileSystem): ProcessLauncher = {
    configType match {
      case DATA_WEAVE_CONFIG_TYPE_NAME => new DefaultWeaveLauncher(projectKind, clientLogger, languageClient, vfs)
      case WTF_CONFIG_TYPE_NAME => new WTFLauncher(projectKind, clientLogger, languageClient, vfs)
      case WITF_CONFIG_TYPE_NAME => new DefaultWeaveLauncher(projectKind, clientLogger, languageClient, vfs)
      case _ => throw new RuntimeException(s"Unable to found a valid launcher for ${configType}.")
    }
  }
}


object JavaWeaveLauncher {

  val WEAVE_RUNNER_MAIN_CLASS = "org.mule.weave.v2.runtime.utils.WeaveRunner"

  def buildJavaProcessBaseArgs(projectKind: ProjectKind): util.ArrayList[String] = {
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
    val dependencyManager: ProjectDependencyManager = projectKind.dependencyManager()
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

    /// Common system properties
    //    args.add(s"-Duser.dir='${projectKind.structure().projectHome.getAbsolutePath}'")
    ///
    args.add(WEAVE_RUNNER_MAIN_CLASS)
    args
  }
}

class DefaultWeaveLauncher(projectKind: ProjectKind,
                           clientLogger: ClientLogger,
                           languageClient: WeaveLanguageClient,
                           vfs: ProjectVirtualFileSystem) extends ProcessLauncher {

  val icon = Icons.vscode

  override type ConfigType = RunMappingConfiguration


  override def launch(config: RunMappingConfiguration, debugging: Boolean): Option[Process] = {

    val builder = new ProcessBuilder()
    val args: util.ArrayList[String] = buildJavaProcessBaseArgs(projectKind)


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
        val sourceFolders = ProjectStructure.mainSourceFolders(projectKind.structure())
        val items: Array[WeaveQuickPickItem] = vfs.listFiles().asScala
          .filter((vf) => {
            //Filter for test only files
            vf.url().endsWith(".dwl") && URLUtils.isChildOfAny(vf.url(), sourceFolders)
          })
          .map((s) => {
            WeaveQuickPickItem(s.getNameIdentifier.toString, icon.file + s.getNameIdentifier.toString)
          }).toArray
        val result = languageClient.weaveQuickPick(WeaveQuickPickParams(
          items = util.Arrays.asList(items: _*),
          title = "Select The DataWeave Script To Run"
        )).get()
        if (!result.cancelled) {
          nameIdentifier = Option(result.itemsId.get(0))
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
      val response = languageClient.weaveQuickPick(WeaveQuickPickParams(
        items = util.Arrays.asList(items: _*),
        title = "Select The Sample Data To Use"
      ))
      val result = response.get()
      if (result.cancelled) {
        None
      } else {
        Some(result.itemsId.get(0))
      }
    } else {
      None
    }
  }

  override def parseArgs(args: Map[String, AnyRef]): RunMappingConfiguration = {
    RunMappingConfiguration(
      args.get(LaunchConfiguration.MAIN_FILE_NAME).map(_.toString),
      args.get("scenario").map(_.toString),
      args.get(LaunchConfiguration.BUILD_BEFORE_PROP_NAME).forall((value) => value.toString == "true"),
      TcpClientProtocol.DEFAULT_PORT
    )
  }
}


class WTFLauncher(projectKind: ProjectKind,
                  clientLogger: ClientLogger,
                  languageClient: WeaveLanguageClient,
                  vfs: ProjectVirtualFileSystem) extends ProcessLauncher {

  val icon = Icons.vscode

  override type ConfigType = RunWTFConfiguration


  override def launch(config: RunWTFConfiguration, debugging: Boolean): Option[Process] = {

    val builder = new ProcessBuilder()
    val args: util.ArrayList[String] = buildJavaProcessBaseArgs(projectKind)


    config.testToRun match {
      case Some(testToRun) if (testToRun.nonEmpty) => {
        args.add(s"-DtestToRun='${testToRun}'")
      }
      case _ => {}
    }

    //
    args.add("--wtest")
    //
    if (debugging) {
      args.add("-debug")
    }

    ///
    config.mayBeTests match {
      case Some(theTests) => {
        val tests = theTests.split(",")
        tests.foreach((theTest) => {
          args.add("-test")
          args.add(theTest)
        })
      }
      case _ => {
        val items: Array[WeaveQuickPickItem] = vfs.listFiles().asScala
          .filter((vf) => {
            vf.url().endsWith(".dwl")
          }).map((s) => {
          WeaveQuickPickItem(s.getNameIdentifier.toString, icon.file + s.getNameIdentifier.toString)
        }).toArray
        val result = languageClient.weaveQuickPick(WeaveQuickPickParams(
          items = util.Arrays.asList(items: _*),
          title = "Select The Test To Run"
        )).get()
        if (!result.cancelled) {
          args.add("-test")
          args.add(result.itemsId.get(0))
        } else {
          clientLogger.logInfo("No Test specified")
          return None
        }
      }
    }
    ///
    builder.command(args)
    Some(builder.start())
  }

  override def parseArgs(args: Map[String, AnyRef]): RunWTFConfiguration = {
    RunWTFConfiguration(
      args.get(LaunchConfiguration.MAIN_FILE_NAME).map(_.toString),
      args.get("testToRun").map(_.toString),
      args.get(LaunchConfiguration.BUILD_BEFORE_PROP_NAME).forall((value) => value.toString == "true"),
      TcpClientProtocol.DEFAULT_PORT
    )
  }
}

