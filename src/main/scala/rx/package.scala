

import annotation.tailrec
import concurrent.Future
import rx.SyncSignals.DynamicSignal


package object rx {

  object NoInitializedException extends Exception()

  type Rx[T] = Flow.Signal[T]
  val Rx = DynamicSignal
  implicit def pimpedSig[T](source: Rx[T]) = Combinators.pimpedSig(source)
  implicit def pimpedFutureSig[T](source: Rx[Future[T]]) = Combinators.pimpedFutureSig(source)
}

