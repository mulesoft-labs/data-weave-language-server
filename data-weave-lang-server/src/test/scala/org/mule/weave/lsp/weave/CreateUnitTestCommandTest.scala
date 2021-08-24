package org.mule.weave.lsp.weave

import org.mule.weave.lsp.DWLspServerUtils.getMavenProjectWorkspace
import org.mule.weave.lsp.DWProject
import org.mule.weave.lsp.commands.Commands
import org.scalatest.FreeSpec
import org.scalatest.Matchers

import java.io.File

class CreateUnitTestCommandTest extends FreeSpec with Matchers {

  val SIMPLE_TEST = """%dw 2.0
                      |import * from dw::test::Tests
                      |import * from dw::test::Asserts
                      |
                      |import test from MyLib
                      |---
                      |"Test test" describedBy [
                      |    "It should do something" in do {
                      |        test() must beObject()
                      |    },
                      |]
                      |
                      |""".stripMargin

  val TWO_SIMPLE_TEST = """%dw 2.0
                      |import * from dw::test::Tests
                      |import * from dw::test::Asserts
                      |
                      |import test from MyLib
                      |---
                      |"Test test" describedBy [
                      |    "It should do something" in do {
                      |        test() must beObject()
                      |    },
                      |    "It should do something" in do {
                      |        test() must beObject()
                      |    },
                      |]
                      |
                      |
                      |""".stripMargin

  "should create a test file for a given function" in {
    val project: DWProject = getMavenProjectWorkspace()
    project.waitForProjectInitialized()

    val workspaceRoot = project.workspaceRoot.toFile
    val file = new File(workspaceRoot, s"src/test/dwtest")
    file.mkdirs()
    val scenario = new File(workspaceRoot, s"src/test/dwtest/MyLib/testTest.dwl")

    assert(!scenario.exists())
    project.runCommand(Commands.DW_CREATE_UNIT_TEST, s"file://${workspaceRoot}/src/main/dw/MyLib.dwl", "0", "18")
    assert(scenario.exists())

    val source = scala.io.Source.fromFile(scenario)
    val content: String = try source.mkString finally source.close()
    content shouldBe SIMPLE_TEST

    project.runCommand(Commands.DW_CREATE_UNIT_TEST, s"file://${workspaceRoot}/src/main/dw/MyLib.dwl", "0", "18")

    val source2 = scala.io.Source.fromFile(scenario)
    val content2 = try source2.mkString finally source2.close()
    content2 shouldBe TWO_SIMPLE_TEST
  }


}
