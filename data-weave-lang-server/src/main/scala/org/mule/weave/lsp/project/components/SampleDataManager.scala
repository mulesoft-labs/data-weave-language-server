package org.mule.weave.lsp.project.components

import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.utils.WeaveDirectoryUtils
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File
import java.io.FilenameFilter

/**
  * Provides the scenarios for Sample Data  for a given mapping
  */
trait SampleDataManager {

  /**
    * Returns the sample data folder for the given NameIdentifier
    *
    * @param nameIdentifier
    * @return
    */
  def searchSampleDataFolderFor(nameIdentifier: NameIdentifier): Option[File]

  /**
    * Create the sample folder for the given name identifier
    *
    * @param nameIdentifier The name Identifier
    * @return
    */
  def createSampleDataFolderFor(nameIdentifier: NameIdentifier): File

  /**
    * List all the scenarios for a given NameIdentifier
    *
    * @param nameIdentifier The NameIdentifier of the mapping
    * @return
    */
  def listScenarios(nameIdentifier: NameIdentifier): Array[Scenario]

  /**
    * Searches for a scenario for a given mapping with the given name
    *
    * @param nameIdentifier The NameIdentifier of the mapping
    * @param scenarioName   The name of the scenario
    * @return
    */
  def searchScenarioByName(nameIdentifier: NameIdentifier, scenarioName: String): Option[Scenario]
}


class WTFSampleDataManager(projectStructure: ProjectStructure, project: Project) extends SampleDataManager {

  override def listScenarios(nameIdentifier: NameIdentifier): Array[Scenario] = {
    searchSampleDataFolderFor(nameIdentifier)
      .map((f) => {
        f.listFiles().map((f) => {
          Scenario(f, f.getName)
        })
      })
      .getOrElse(Array.empty)
  }

  def searchSampleDataFolderFor(nameIdentifier: NameIdentifier): Option[File] = {
    val options: Array[File] = wtfFolders()
    val result = options
      .map((dwitFolder) => new File(dwitFolder, WeaveDirectoryUtils.toFolderName(nameIdentifier)))
      .find((scenario) => {
        scenario.exists()
      })
    result
  }

  private def wtfFolders(): Array[File] = {
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

  override def searchScenarioByName(nameIdentifier: NameIdentifier, scenarioName: String): Option[Scenario] = {
    listScenarios(nameIdentifier)
      .find((s) => {
        s.file.getName.equals(scenarioName)
      })
  }

  override def createSampleDataFolderFor(nameIdentifier: NameIdentifier): File = {
    val dwitFolder = new File(new File(new File(project.home(), "src"), "test"), WeaveDirectoryUtils.DWIT_FOLDER)
    val sampleFolder = new File(dwitFolder, WeaveDirectoryUtils.toFolderName(nameIdentifier))
    sampleFolder.mkdir()
    sampleFolder
  }
}


class DefaultSampleDataManager(weaveHome: File) extends SampleDataManager {

  override def listScenarios(nameIdentifier: NameIdentifier): Array[Scenario] = {
    searchSampleDataFolderFor(nameIdentifier)
      .map((f) => {
        f.listFiles().map((f) => {
          Scenario(f, f.getName)
        })
      })
      .getOrElse(Array.empty)
  }

  def searchSampleDataFolderFor(nameIdentifier: NameIdentifier): Option[File] = {
    val samplesForThisFile: File = getSampleFolderFor(nameIdentifier)
    if (samplesForThisFile.exists()) {
      Some(samplesForThisFile)
    } else {
      None
    }

  }

  private def getSampleFolderFor(nameIdentifier: NameIdentifier) = {
    val sampleFolder = new File(weaveHome, "samples")
    val samplesForThisFile = new File(sampleFolder, WeaveDirectoryUtils.toFolderName(nameIdentifier))
    samplesForThisFile
  }

  override def searchScenarioByName(nameIdentifier: NameIdentifier, scenarioName: String): Option[Scenario] = {
    listScenarios(nameIdentifier)
      .find((s) => {
        s.file.getName.equals(scenarioName)
      })
  }

  override def createSampleDataFolderFor(nameIdentifier: NameIdentifier): File = {
    val samplesForThisFile: File = getSampleFolderFor(nameIdentifier)
    samplesForThisFile.mkdir()
    samplesForThisFile
  }
}


case class Scenario(file: File, name: String) {

  def inputs(): File = {
    new File(file, "inputs")
  }

  def expected(): Option[File] = {
    val files = file.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith("out.")
      }
    })
    Option(files).flatMap(_.headOption)
  }

}

