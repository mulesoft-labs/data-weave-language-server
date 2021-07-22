package org.mule.weave.lsp.ui.wizard

import org.mule.weave.lsp.extension.client.WeaveButton
import org.mule.weave.lsp.extension.client.WeaveInputBoxParams
import org.mule.weave.lsp.extension.client.WeaveInputBoxResult
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.ui.utils.Buttons.Button

import scala.collection.JavaConverters.seqAsJavaListConverter

class InputWidgetBuilder[A](client: WeaveLanguageClient) extends WidgetBuilder[String, A] {
  var selectionProvider: A => Option[String] = (_) => Option.empty

  var default: String = _

  var prompt: String = _

  var totalSteps: Int = _

  var stepNumber: Int = _

  var title: String = _

  var buttons: Seq[(Button, (A => WidgetResult[A]))] = Seq()

  var resultMapping: (A, String) => A = _

  override def title(title: String): InputWidgetBuilder[A] = {
    this.title = title
    this
  }

  override def stepNumber(number: Int): InputWidgetBuilder[A] = {
    this.stepNumber = number
    this
  }

  override def totalSteps(number: Int): InputWidgetBuilder[A] = {
    this.totalSteps = number
    this
  }


  def default(default: String): InputWidgetBuilder[A] = {
    this.default = default
    this
  }

  def prompt(prompt: String): InputWidgetBuilder[A] = {
    this.prompt = prompt
    this

  }

  override def button(button: Button, function: A => WidgetResult[A]): InputWidgetBuilder[A] = {
    buttons = buttons :+ (button, function)
    this
  }

  override def create(): Widget[A] = new InputWidget(client, this)

  override def result(function: (A, String) => A): InputWidgetBuilder[A] = {
    resultMapping = function
    this
  }

  override def selectionProvider(function: (A => Option[String])): InputWidgetBuilder[A] = {
    this.selectionProvider = function
    this
  }
}

class InputWidget[A](languageClient: WeaveLanguageClient, inputWidgetBuilder: InputWidgetBuilder[A]) extends Widget[A] {

  var inputWidgetButtons: Map[String, (A => WidgetResult[A])] = Map()

  override def show(resultToFill: A): WidgetResult[A] = {
    val weaveButtons = inputWidgetBuilder.buttons map (buttonAndAction => {
      val (button, action) = buttonAndAction
      this.inputWidgetButtons = this.inputWidgetButtons updated(button.id, action)
      WeaveButton(id = button.id, iconPath = button.icon)
    })
    val default = inputWidgetBuilder.selectionProvider.apply(resultToFill).orElse(Option(inputWidgetBuilder.default)).getOrElse("")
    val inputBoxParams = WeaveInputBoxParams(title = inputWidgetBuilder.title, prompt = inputWidgetBuilder.prompt, value = default, step = inputWidgetBuilder.stepNumber, totalSteps = inputWidgetBuilder.totalSteps, buttons = weaveButtons.toList.asJava)

    val resultValue: WeaveInputBoxResult = languageClient.weaveInputBox(inputBoxParams).get()

    val mappedResult = inputWidgetBuilder.resultMapping.apply(resultToFill, resultValue.value)
    val widgetResult = this.inputWidgetButtons.getOrElse(resultValue.buttonPressedId, (result: A) => new WidgetResult[A](resultValue.cancelled, mappedResult, resultValue.buttonPressedId)).apply(mappedResult)
    widgetResult
  }
}
