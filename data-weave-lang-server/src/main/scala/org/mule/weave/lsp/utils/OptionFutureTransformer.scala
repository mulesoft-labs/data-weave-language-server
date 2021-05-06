package org.mule.weave.lsp.utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
object WeaveEnrichments {

  implicit class OptionFutureTransformer[A](state: Future[Option[A]]) {
    def flatMapOption[B](
                          f: A => Future[Option[B]]
                        )(implicit ec: ExecutionContext): Future[Option[B]] =
      state.flatMap(_.fold(Future.successful(Option.empty[B]))(f))

    def mapOption[B](
                      f: A => Future[B]
                    )(implicit ec: ExecutionContext): Future[Option[B]] =
      state.flatMap(
        _.fold(Future.successful(Option.empty[B]))(f(_).liftOption)
      )

    def mapOptionInside[B](
                            f: A => B
                          )(implicit ec: ExecutionContext): Future[Option[B]] =
      state.map(
        _.map(f)
      )
  }

  implicit class XtensionScalaFuture[A](future: Future[A]) {
    def liftOption(implicit
                   ec: ExecutionContext
                  ): Future[Option[A]] = future.map(Some(_))

  }
}
