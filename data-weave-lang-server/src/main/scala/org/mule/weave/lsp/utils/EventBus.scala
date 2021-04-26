package org.mule.weave.lsp.utils

import java.util.concurrent.ExecutorService
import java.util.logging.Level
import java.util.logging.Logger
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class EventBus(executorService: ExecutorService) {

  val logger: Logger = Logger.getLogger(getClass.getName)

  private val listeners = new mutable.HashMap[EventType[_], ArrayBuffer[EventHandler]]

  def unRegister[T <: EventHandler](evenType: EventType[T], eventHandler: T): Unit = {
    listeners.get(evenType).foreach((a) => {
      a.-=(eventHandler)
    })
  }

  def register[T <: EventHandler](evenType: EventType[T], handler: T): EventRegistrationHandler[T] = {
    listeners.getOrElseUpdate(evenType, ArrayBuffer[EventHandler]()).+=(handler)
    EventRegistrationHandler(this, evenType, handler)
  }

  def fire(event: Event): Unit = {
    listeners.get(event.getType) match {
      case Some(listeners) => {
        executorService.execute(() => {
          listeners.foreach((l) => {
            Try(event.dispatch(l.asInstanceOf[event.T])) match {
              case Success(_) =>
              case Failure(exception) => {
                logger.log(Level.WARNING, "Exception on dispatching :" + event, exception)
              }
            }
          })
        })
      }
      case None =>
    }
  }
}


trait Event {

  type T <: EventHandler

  def getType: EventType[T]

  def dispatch(handler: T): Unit
}

case class EventType[T <: EventHandler](kind: String) {}

trait EventHandler {}

case class EventRegistrationHandler[T <: EventHandler](bus: EventBus, evenType: EventType[T], handler: T) {
  def unregister(): Unit = {
    bus.unRegister(evenType, handler)
  }

  def register(): Unit = {
    bus.register(evenType, handler)
  }
}