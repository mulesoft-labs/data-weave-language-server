package org.mule.weave.lsp

import com.google.common.util.concurrent.ThreadFactoryBuilder

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object IDEExecutors {


  private val defaultService = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
      .setNameFormat("dw-lang-server-language-%d\"")
      .setDaemon(true)
      .build()
  )

  private val debuggerService = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder()
      .setNameFormat("dw-lang-server-debugger-%d\"")
      .setDaemon(true)
      .build()
  )

  private val previewService = Executors.newFixedThreadPool(1,
    new ThreadFactoryBuilder()
      .setNameFormat("indexing-%d\"")
      .setDaemon(true)
      .build()
  )

  private val indexingService = Executors.newFixedThreadPool(8,
    new ThreadFactoryBuilder()
      .setNameFormat("indexing-%d\"")
      .setDaemon(true)
      .build()
  )

  /**
    * Event bus thread pool has only one dispatcher so the events order can be guarantied
    */
  private val eventsService = Executors.newFixedThreadPool(
    1,
    new ThreadFactoryBuilder()
      .setNameFormat("dw-events-%d\"")
      .setDaemon(true)
      .build()
  )

  def debuggingExecutor(): ExecutorService = {
    debuggerService
  }

  def defaultExecutor(): ExecutorService = {
    defaultService
  }

  def eventsExecutor(): ExecutorService = {
    eventsService
  }

  def indexingExecutor(): ExecutorService = {
    indexingService
  }

  def previewExecutor(): ExecutorService = {
    previewService
  }

}
