package org.mule.weave.lsp.project.components

import org.mule.weave.lsp.utils.WTFUtils
import org.mule.weave.v2.parser.ast.variables.NameIdentifier

import java.io.File

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


class WTFSampleDataManager(projectStructure: ProjectStructure) extends SampleDataManager {

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
      .map((dwitFolder) => new File(dwitFolder, WTFUtils.toFolderName(nameIdentifier)))
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
              f.getName.equals(WTFUtils.DWIT_FOLDER)
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
    val dwitFolder = new File(new File(new File(projectStructure.projectHome, "src"), "test"), WTFUtils.DWIT_FOLDER)
    val sampleFolder = new File(dwitFolder, WTFUtils.toFolderName(nameIdentifier))
    sampleFolder.mkdir()
    sampleFolder
  }
}


case class Scenario(file: File, name: String) {}

