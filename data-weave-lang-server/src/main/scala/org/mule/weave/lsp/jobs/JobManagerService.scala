package org.mule.weave.lsp.jobs

import org.mule.weave.lsp.extension.client.JobEndedParams
import org.mule.weave.lsp.extension.client.JobStartedParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.services.ToolingService

import java.util.UUID
import java.util.concurrent.Executor

class JobManagerService(executor: Executor, weaveLanguageClient: WeaveLanguageClient) extends ToolingService {

  def submit(job: Job): Unit = {
    executor.execute(new Runnable {
      override def run(): Unit = {
        execute(job)
      }
    })
  }

  def execute(job: Job): Unit = {
    val jobId = UUID.randomUUID()
    try {
      weaveLanguageClient.notifyJobStarted(JobStartedParams(id = jobId.toString, label = job.label(), description = job.description()))
      job.run()
    } finally {
      weaveLanguageClient.notifyJobEnded(JobEndedParams(id = jobId.toString))
    }
  }

  def execute(job: Runnable, theLabel: String, theDescription: String): Unit = {
    execute(new Job {
      override def description(): String = {
        theDescription
      }

      override def label(): String = {
        theLabel
      }

      override def doTheJob(): Unit = {
        job.run()
      }
    })
  }

  def submit(job: Runnable, theLabel: String, theDescription: String): Unit = {
    submit(new Job {
      override def description(): String = {
        theDescription
      }

      override def label(): String = {
        theLabel
      }

      override def doTheJob(): Unit = {
        job.run()
      }
    })
  }
}

trait Job {
  @transient
  var canceled: Boolean = false
  var hostThread: Thread = _

  def description(): String

  def label(): String

  final def run(): Unit = {
    hostThread = Thread.currentThread()
    doTheJob()
  }

  def doTheJob(): Unit

  def cancel(): Unit = {
    hostThread.interrupt()
    canceled = true
  }
}



