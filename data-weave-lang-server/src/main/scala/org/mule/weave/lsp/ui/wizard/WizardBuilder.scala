package org.mule.weave.lsp.ui.wizard

trait WizardBuilder[T] {

  def modelProvider(provider: () => T): WizardBuilder[T]

  def step(step: WizardStepBuilder[T]): WizardBuilder[T]

  def create(): Wizard[T]

}

trait Wizard[T]{

  def open() : T

}
