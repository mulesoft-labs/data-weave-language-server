package org.mule.weave.lsp.jobs

import org.mule.weave.lsp.extension.client.JobEndedParams
import org.mule.weave.lsp.extension.client.JobStartedParams
import org.mule.weave.lsp.extension.client.WeaveLanguageClient
import org.mule.weave.lsp.services.ToolingService

import java.util.UUID
import java.util.concurrent.Executor

class JobManagerService(executor: Executor, weaveLanguageClient: WeaveLanguageClient) extends ToolingService {
  /**
    * Schedules a job to be executed in the JobManager executor pool
    *
    * @param job The job to be schedule to execute
    */
  def schedule(job: Job): Unit = {
    executor.execute(new Runnable {
      override def run(): Unit = {
        execute(job)
      }
    })
  }

  /**
    * Executes the specified Runnable in the current thread in a Sync Way
    *
    * @param job The job to be executed
    */
  def execute(job: Job): Unit = {
    val jobId = UUID.randomUUID()
    try {
      weaveLanguageClient.notifyJobStarted(JobStartedParams(id = jobId.toString, label = job.label(), description = job.description()))
      job.run()
    } finally {
      weaveLanguageClient.notifyJobEnded(JobEndedParams(id = jobId.toString))
    }
  }

  /**
    * Executes the specified Runnable in the current thread in a Sync Way
    *
    * @param task           The task to be executed
    * @param theLabel       The label
    * @param theDescription The description
    */
  def execute(task: Runnable, theLabel: String, theDescription: String): Unit = {
    execute(new Job {
      override def description(): String = {
        theDescription
      }

      override def label(): String = {
        theLabel
      }

      override def doTheJob(): Unit = {
        task.run()
      }
    })
  }

  /**
    * Schedules a job to be executed with the specified label and description
    *
    * @param job            The job to be scheduled
    * @param theLabel       The label
    * @param theDescription The description
    */
  def schedule(job: Runnable, theLabel: String, theDescription: String): Unit = {
    schedule(new Job {
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



