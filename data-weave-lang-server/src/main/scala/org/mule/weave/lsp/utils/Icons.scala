package org.mule.weave.lsp.utils

abstract class Icons {
  def rocket: String
  def sync: String
  def alert: String
  def rightArrow: String
  def ellipsis: String
  def info: String
  def check: String
  def findsuper: String
  def folder: String
  def github: String
  def error: String
  final def all: List[String] =
    List(
      rocket,
      sync,
      alert,
      info,
      check,
      findsuper,
      folder,
      github,
      error
    )
}
object Icons {
  def translate(from: Icons, to: Icons, message: String): String = {
    from.all.zip(to.all).collectFirst {
      case (a, b) if message.startsWith(a) =>
        b + message.stripPrefix(a)
    }
  }.getOrElse(message)
  def default: Icons = none
  def fromString(value: String): Icons =
    value match {
      case "octicons" | "vscode" => vscode
      case "unicode" => unicode
      case _ => none
    }

  case object unicode extends Icons {
    override def rocket: String = "🚀 "
    override def sync: String = "🔄 "
    override def alert: String = "⚠️ "
    override def info: String = "ℹ️ "
    override def check: String = "✅ "
    override def findsuper: String = "⏫ "
    override def folder: String = "📁 "
    override def github: String = ""
    override def error: String = "❌"
    override def rightArrow: String = "⇒"
    override def ellipsis: String = "…"
  }
  case object none extends Icons {
    override def rocket: String = ""
    override def sync: String = ""
    override def alert: String = ""
    override def info: String = ""
    override def check: String = ""
    override def findsuper: String = ""
    override def folder: String = ""
    override def github: String = ""
    override def error: String = ""
    override def rightArrow: String = "=>"
    override def ellipsis: String = "..."
  }
  // icons for vscode can be found here("Icons in Labels"):
  // https://code.visualstudio.com/api/references/icons-in-labels
  case object vscode extends Icons {
    override def rocket: String = "$(rocket) "
    override def sync: String = "$(sync) "
    override def alert: String = "$(alert) "
    override def info: String = "$(info) "
    override def check: String = "$(check) "
    override def findsuper: String = "$(arrow-up)"
    override def folder: String = "$(folder)"
    override def github: String = "$(github) "
    override def error: String = "$(error)"
    override def rightArrow: String = "⇒"
    override def ellipsis: String = "…"
  }
}
