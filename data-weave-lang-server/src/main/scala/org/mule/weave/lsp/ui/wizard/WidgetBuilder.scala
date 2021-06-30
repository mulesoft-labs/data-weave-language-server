package org.mule.weave.lsp.ui.wizard

import org.mule.weave.lsp.ui.utils.Buttons.Button

trait WidgetBuilder[A, T] {

  def title(title: String): WidgetBuilder[A, T]

  def stepNumber(number: Int): WidgetBuilder[A, T]

  def totalSteps(number: Int): WidgetBuilder[A, T]

  def button(button: Button, function: (T => WidgetResult[T])): WidgetBuilder[A, T]

  def result(function: (T, A) => T): WidgetBuilder[A, T]

  def selectionProvider(function: (T) => Option[A]): WidgetBuilder[A, T]

  def create(): Widget[T]

}

trait Widget[T] {

  def show(resultObject: T): WidgetResult[T]

}

case class WidgetResult[T](cancelled: Boolean, result: T, buttonPressedId: String)
