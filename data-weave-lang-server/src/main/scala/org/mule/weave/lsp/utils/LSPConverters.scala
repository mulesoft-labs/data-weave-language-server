package org.mule.weave.lsp.utils

import org.eclipse.lsp4j
import org.mule.weave.v2.parser.location.Position
import org.mule.weave.v2.parser.location.WeaveLocation

object LSPConverters extends AnyRef {

  implicit def toPosition(endPosition: Position): lsp4j.Position = {
    val position = new lsp4j.Position()
    val column = if (endPosition.column < 0) 0 else endPosition.column - 1
    val line = if (endPosition.line < 0) 0 else endPosition.line - 1
    position.setCharacter(column)
    position.setLine(line)
    position
  }

  implicit def toRange(location: WeaveLocation): lsp4j.Range = {
    val range = new lsp4j.Range()
    range.setEnd(toPosition(location.endPosition))
    range.setStart(toPosition(location.startPosition))
    range
  }

}
