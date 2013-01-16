package rx

import util.{Try, Failure, Success}
import concurrent.{ExecutionContext, Future}
import concurrent.duration.{FiniteDuration, Duration}
import akka.actor.ActorSystem
import Flow.Signal
import AsyncSignals._
import SyncSignals._

/**
 * A collection of methods which allow you to construct Rxs from other
 * Rxs using method chaining
 */
object Combinators{
  implicit class pimpedSig[T](source: Signal[T]) {

    def skipFailures = filterSig(source)((oldTry, newTry) => newTry.isSuccess)

    def skipTry(predicate: (Try[T], Try[T]) => Boolean) = filterTry((x, y) => !predicate(x, y))
    def filterTry(predicate: (Try[T], Try[T]) => Boolean) = filterSig(source)(predicate)

    def skipDiff(successPred: (T, T) => Boolean = _!=_,
             failurePred: (Throwable, Throwable) => Boolean = _!=_) = {
      filterDiff(
        (x, y) => !successPred(x, y) ,
        (x, y) => !failurePred(x, y)
      )
    }
    def filter(successPred: T => Boolean, failurePred: Throwable => Boolean = x => true) = {
      new WrapSig[T, T](source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) if failurePred(thrown) => Failure(thrown)
          case (old, _) => old
        }
      )
    }
    def filterDiff(successPred: (T, T) => Boolean = _!=_,
               failurePred: (Throwable, Throwable) => Boolean = _!=_) = {

      filterSig[T](source)(
        (x, y) => (x, y) match {
          case (Success(a), Success(b)) => successPred(a, b)
          case (Failure(a), Failure(b)) => failurePred(a, b)
          case _ => true
        }
      )
    }

    def map[A](f: T => A) = new WrapSig[A, T](source)((x, y) => y.map(f))

    def debounce(interval: FiniteDuration)(implicit system: ActorSystem, ex: ExecutionContext) = {
      new DebouncedSig[T](source, interval)
    }
  }
  implicit class pimpedFutureSig[T](source: Signal[Future[T]]){
    def async(default: T, target: AsyncSignals.type => T => AsyncSignals.Target[T] = xml => AsyncSignals.BaseTarget[T])(implicit executor: ExecutionContext) = {
      new AsyncSig(default, source, target(AsyncSignals))
    }
  }



}

