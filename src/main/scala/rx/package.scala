

import annotation.tailrec
import concurrent.Future
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import rx.SyncSignals.DynamicSignal


package object rx {

  object NoInitializedException extends Exception()

  type Rx[+T] = Flow.Signal[T]
  val Rx = DynamicSignal

  val Timer = AsyncSignals.Timer
  implicit def pimpedFutureSignal[T](source: Rx[Future[T]]) = Combinators.pimpedFutureSignal(source)

  case class Atomic[T](t: T) extends AtomicReference[T](t){
    def apply() = get
    def update(t: T) = set(t)
    @tailrec final def spinSet(transform: T => T): Unit = {
      val oldV = this()
      val newV = transform(oldV)
      if (!compareAndSet(oldV, newV)) {
        println("retry")
        spinSet(transform)
      }
    }
    @tailrec final def spinSetOpt(transform: T => Option[T]): Boolean = {
      val oldV = this()
      val newVOpt = transform(oldV)
      newVOpt match{
        case Some(newV) => if (!compareAndSet(oldV, newV)) {
          println("retry")
          spinSetOpt(transform)
        } else true
        case None => false
      }

    }
  }
}

