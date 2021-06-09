package org.mule.weave.lsp.ui.wizard

import org.mule.weave.lsp.ui.utils.Buttons

class DefaultWizardStep[T](widget: Widget[T], val finalStep: Boolean) extends WizardStep[T] {


  override def run(objectModel: T): WizardStepResult[T] = {
    val widgetResult = widget.show(objectModel)
    val value = widgetResult match {
      case WidgetResult(_, _, buttonId) if (buttonId != null) && buttonId.equals(Buttons.back().id) => StepResult.BACK
      case WidgetResult(_, _, buttonId) if (buttonId != null) && buttonId.equals("finish") => StepResult.FINISH
      case _ => if (finalStep) StepResult.FINISH else StepResult.NEXT
    }
    WizardStepResult(value, widgetResult.result)
  }
}
