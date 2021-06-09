package org.mule.weave.lsp.ui.wizard

class DefaultWizardBuilder[T] extends WizardBuilder[T] {

  var steps: Array[WizardStepBuilder[T]] = Array()
  var provider: () => T = _

  override def step(step: WizardStepBuilder[T]): WizardBuilder[T] = {
    val length = steps.length
    if (length == 0) {
      step.allowBack(false)
    }
    step.stepNumber(length + 1)
    steps = steps :+ step
    this
  }

  override def modelProvider(provider: () => T): WizardBuilder[T] = {
    this.provider = provider
    this
  }

  override def create(): Wizard[T] = new DefaultWizard[T](steps.map((wizardStepBuilder) => {
    wizardStepBuilder.totalSteps(steps.length)
    wizardStepBuilder.build()
  }), provider.apply());
}

private class DefaultWizard[T](steps: Array[WizardStep[T]], val model: T) extends Wizard[T] {


  override def open(): T = {
    runStep(model, 0, steps)
  }

  def runStep(model: T, position: Int, steps: Array[WizardStep[T]]): T = {
    steps(position).run(model) match {
      case WizardStepResult(StepResult.NEXT, modelResult) => runStep(modelResult, position + 1, steps)
      case WizardStepResult(StepResult.BACK, modelResult) => runStep(modelResult, position - 1, steps)
      case WizardStepResult(StepResult.FINISH, modelResult) => modelResult
      case WizardStepResult(StepResult.CANCEL, modelResult) => modelResult
    }
  }
}


