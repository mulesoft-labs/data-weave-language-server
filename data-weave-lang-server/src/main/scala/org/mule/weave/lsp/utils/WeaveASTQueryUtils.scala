package org.mule.weave.lsp.utils

import org.mule.weave.v2.parser.ast.AstNode
import org.mule.weave.v2.parser.ast.header.directives.ImportDirective
import org.mule.weave.v2.parser.ast.structure.DocumentNode

/**
  * Helper class that allows to do queries over AST. This is an extension for ASTUtils that is in DataWeave
  */
object WeaveASTQueryUtils {

  val WTF = "WTF"
  val BAT = "BAT"
  val MAPPING = "MAPPING"

  def fileKind(maybeAstNode: Option[AstNode]): Option[String] = {
    maybeAstNode
      .flatMap({
        case dn: DocumentNode => {
          if (hasImport(dn, "dw::test::Tests")) {
            Some(WTF)
          } else if (hasImport(dn, "bat::Core") || hasImport(dn, "bat::BDD")) {
            Some(BAT)
          } else {
            Some(MAPPING)
          }
        }
        case _ => None
      })
  }

  def hasImport(dn: DocumentNode, wtfImport: String): Boolean = {
    dn.header.directives.collect({
      case id: ImportDirective => id
    }).exists((id) => {
      id.importedModule.elementName.name.equals(wtfImport)
    })
  }

}
