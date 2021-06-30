package org.mule.weave.lsp.ui.wizard

import org.mule.weave.lsp.extension.client.WeaveButton
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.extension.client.WeaveQuickPickItem
import org.mule.weave.lsp.extension.client.WeaveQuickPickParams
import org.mule.weave.lsp.ui.utils.Buttons.Button

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter

class QuickPickWidgetBuilder[A](val client: WeaveLanguageClient) extends WidgetBuilder[List[String], A] {

  var itemProvider: A => List[(WeaveQuickPickItem, A => WidgetResult[A])] = _

  var totalSteps: Int = _

  var stepNumber: Int = _

  var title: String = _

  var buttons: Seq[(Button, (A => WidgetResult[A]))] = Seq()

  var resultMapping: (A, List[String]) => A = _

  var selectionProvider: A => Option[List[String]] = (_) => Option.empty

  override def title(title: String): QuickPickWidgetBuilder[A] = {
    this.title = title
    this
  }

  override def stepNumber(number: Int): QuickPickWidgetBuilder[A] = {
    this.stepNumber = number
    this
  }

  override def totalSteps(number: Int): QuickPickWidgetBuilder[A] = {
    this.totalSteps = number
    this
  }

  override def button(button: Button, function: A => WidgetResult[A]): QuickPickWidgetBuilder[A] = {
    buttons = buttons :+ (button, function)
    this
  }

  def itemProvider(itemProvider: A => List[(WeaveQuickPickItem, A => WidgetResult[A])]): QuickPickWidgetBuilder[A] = {
    this.itemProvider = itemProvider
    this
  }

  override def create(): Widget[A] = {
    new QuickPickWidget(client, this)
  }

  override def result(function: (A, List[String]) => A): QuickPickWidgetBuilder[A] = {
    resultMapping = function
    this
  }

  override def selectionProvider(function: (A) => Option[List[String]]): QuickPickWidgetBuilder[A] = {
    selectionProvider = function
    this
  }


}

class QuickPickWidget[T](languageClient: WeaveLanguageClient, quickInputWidgetBuilder: QuickPickWidgetBuilder[T]) extends Widget[T] {

  var buttons: Map[String, (T => WidgetResult[T])] = Map()
  var items: Map[String, (T => WidgetResult[T])] = Map()

  override def show(resultToFill: T): WidgetResult[T] = {
    val selectedItems = quickInputWidgetBuilder.selectionProvider.apply(resultToFill).getOrElse(List())
    val buttons = quickInputWidgetBuilder.buttons map (buttonAndAction => {
      val (button, action) = buttonAndAction
      this.buttons = this.buttons updated(button.id, action)
      WeaveButton(id = button.id, iconPath = button.icon)
    })
    val items = quickInputWidgetBuilder.itemProvider.apply(resultToFill) map (itemAndAction => {
      var (item, action) = itemAndAction
      if (selectedItems.contains(item.id)) {
        item = WeaveQuickPickItem(item.id, item.label, item.description, item.detail, item.alwaysShow, true)
      }
      this.items = this.items updated(item.id, action)
      item
    })
    val inputBoxParams = WeaveQuickPickParams(title = quickInputWidgetBuilder.title, step = quickInputWidgetBuilder.stepNumber, totalSteps = quickInputWidgetBuilder.totalSteps, buttons = buttons.toList.asJava, items = items.toList.asJava)
    val value = languageClient.weaveQuickPick(inputBoxParams).get()
    val mappedResult = quickInputWidgetBuilder.resultMapping.apply(resultToFill, (value.itemsId asScala).toList)
    var widgetResult = this.buttons.getOrElse(value.buttonPressedId, (result: T) => new WidgetResult[T](value.cancelled, mappedResult, value.buttonPressedId)).apply(mappedResult)
    if (value.itemsId != null && !value.itemsId.isEmpty) {
      widgetResult = this.items.getOrElse(value.itemsId.get(0), (result: T) => new WidgetResult[T](value.cancelled, widgetResult.result, value.buttonPressedId)).apply(widgetResult.result)
    }
    widgetResult
  }
}
