package org.mule.weave.lsp.ui.wizard

import org.mule.weave.lsp.ui.utils.Buttons
import org.mule.weave.lsp.ui.utils.Buttons.Button

class DefaultWizardStepBuilder[T] extends WizardStepBuilder[T]{

  var widgetBuilder: WidgetBuilder[_,T] = null


  var allowedToMoveBack : Boolean = true
  var allowedToFinish : Boolean = false
  var skippable : Boolean = false
  var stepNumber : Int = _
  var totalSteps : Int = _


  override def skippable(skippable: Boolean): WizardStepBuilder[T] = {
    this.skippable = skippable
    this
  }

  override def allowToFinish(allowedToFinish: Boolean): WizardStepBuilder[T] = {
    this.allowedToFinish = allowedToFinish
    this
  }

  override def allowBack(allowedToMoveBack: Boolean): WizardStepBuilder[T] = {
    this.allowedToMoveBack = allowedToMoveBack
    this
  }

  override def widgetBuilder(widgetBuilder: WidgetBuilder[_,T]): WizardStepBuilder[T]= {
    this.widgetBuilder = widgetBuilder
    this
  }

  override def stepNumber(stepNumber: Int): WizardStepBuilder[T] = {
    this.stepNumber = stepNumber
    this
  }

  override def totalSteps(totalSteps: Int): WizardStepBuilder[T] = {
    this.totalSteps = totalSteps
    this
  }


  override def build(): WizardStep[T] = {
    if (allowedToMoveBack){
      widgetBuilder.button(Buttons.back(), previousResult => new WidgetResult[T](false, previousResult,"back"))
    }
    widgetBuilder.stepNumber(stepNumber)
    widgetBuilder.totalSteps(totalSteps)
    new DefaultWizardStep[T](widgetBuilder.create(), stepNumber == totalSteps)
  }


}
