package org.mule.weave.lsp.utils

import org.mule.weave.lsp.project.components.ProjectStructure
import org.mule.weave.lsp.project.components.RootKind
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
  * Helper class that works with DataWeave well known directories and constants
  */
object WeaveDirectoryUtils {

  var verbose: Boolean = false
  val DWIT_FOLDER = "dwit"
  val DWTest_FOLDER = "dwtest"
  private val logger: Logger = Logger.getLogger(getClass.getName)

  /**
    * Returns the DW home directory if exists it can be overwritten with env variable DW_HOME
    *
    * @return The home directory
    */
  def getDWHome(): File = {
    val homeUser = getUserHome
    val weavehome = System.getenv("DW_HOME")
    if (weavehome != null) {
      val home = new File(weavehome)
      home
    } else {
      if (verbose) {
        logger.log(Level.INFO, "Env not working trying home directory")
      }
      val defaultDWHomeDir = new File(homeUser, ".dw")
      if (!defaultDWHomeDir.exists()) {
        defaultDWHomeDir.mkdirs()
      }
      defaultDWHomeDir

    }
  }

  def getUserHome(): File = {
    new File(System.getProperty("user.home"))
  }

  /**
    * Returns the DW home directory if exists it can be overwritten with env variable DW_HOME
    *
    * @return The home directory
    */
  def getWorkingHome(): File = {
    val weavehome = System.getenv("DW_WORKING_PATH")
    if (weavehome != null) {
      val home = new File(weavehome)
      home
    } else {
      new File(getDWHome(), "tmp")
    }
  }

  /**
    * Returns the DW home directory if exists it can be overwritten with env variable DW_HOME
    *
    * @return The home directory
    */
  def getCacheHome(): File = {
    val weavehome = System.getenv("DW_CACHE_PATH")
    if (weavehome != null) {
      val home = new File(weavehome)
      home
    } else {
      new File(getDWHome(), "cache")
    }
  }

  /**
    * Returns the directory where all default jars are going to be present. It can be overwriten with DW_LIB_PATH
    *
    * @return The file
    */
  def getLibPathHome(): File = {
    val weavehome = System.getenv("DW_LIB_PATH")
    if (weavehome != null) {
      val home = new File(weavehome)
      home
    } else {
      new File(getDWHome(), "libs")
    }
  }

  def sanitizeFilename(inputName: String): String = {
    inputName.replaceAll("[^a-zA-Z0-9-_\\.]", "_")
  }

  def toFolderName(nameIdentifier: NameIdentifier): String = {
    nameIdentifier.name.replaceAll(NameIdentifier.SEPARATOR, "-")
  }

  def wtfIntegrationTestFolders(projectStructure: ProjectStructure): Array[File] = {
    projectStructure.modules
      .flatMap((m) => {
        m.roots.flatMap((root) => {
          if (root.kind == RootKind.TEST) {
            root.sources.find((f) => {
              f.getName.equals(WeaveDirectoryUtils.DWIT_FOLDER)
            })
          } else {
            None
          }
        })
      })
  }

  def wtfUnitTestFolder(projectStructure: ProjectStructure): Array[File] = {
    projectStructure.modules
      .flatMap((m) => {
        m.roots.flatMap((root) => {
          if (root.kind == RootKind.TEST) {
            root.sources.find((f) => {
              f.getName.equals(WeaveDirectoryUtils.DWTest_FOLDER)
            })
          } else {
            None
          }
        })
      })
  }
}

