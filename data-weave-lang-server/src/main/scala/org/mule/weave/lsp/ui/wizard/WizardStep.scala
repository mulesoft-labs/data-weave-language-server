package org.mule.weave.lsp.ui.wizard

import org.mule.weave.lsp.ui.wizard.StepResult.StepResult

trait WizardStepBuilder[A] {

  def skippable(skippable: Boolean): WizardStepBuilder[A]

  def allowToFinish(allowedToFinish: Boolean) : WizardStepBuilder[A]

  def allowBack(allowedToMoveBack: Boolean): WizardStepBuilder[A]

  def widgetBuilder(widgetBuilder: WidgetBuilder[_,A]): WizardStepBuilder[A]

  def stepNumber(number: Int): WizardStepBuilder[A]

  def totalSteps(number: Int): WizardStepBuilder[A]

  def build(): WizardStep[A]
}

object StepResult extends Enumeration {
  type StepResult = Value
  val NEXT,BACK,FINISH,CANCEL = Value
}

case class WizardStepResult[A](stepResult: StepResult, model: A)

trait WizardStep[A]{

  def run(objectModel: A): WizardStepResult[A]
}



