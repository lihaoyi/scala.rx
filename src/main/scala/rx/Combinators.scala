package rx

import util.{Try, Failure, Success}
import concurrent.{ExecutionContext, Future}
import concurrent.duration._
import akka.actor.ActorSystem
import Flow.Signal
import AsyncSignals._
import concurrent.duration._
import SyncSignals._

/**
 * A collection of methods which allow you to construct Rxs from other
 * Rxs using method chaining
 */
object Combinators{
  trait EmitterMethods[+T]{ source: Flow.Emitter[T] =>
  }

  trait SignalMethods[+T]{ source: Signal[T] =>

    /**
     * Creates a new Rx which ignores Failure conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value
     */
    def skipFailures = filterSig((oldTry, newTry) => newTry.isSuccess)

    /**
     * Creates a new Rx which ignores specific Success conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value if the new value fails the filter. Optionally takes a failurePred, allowing
     * it to filter the Failure conditions as well.
     */
    def filter(successPred: T => Boolean, failurePred: Throwable => Boolean = x => true): Signal[T] = {
      new FilterSignal(source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) if failurePred(thrown) => Failure(thrown)
          case (old, _) => old
        }
      )
    }



    /**
     * Creates a new Rx which contains the value of the old Rx, except transformed by some
     * function.
     */
    def map[A](f: T => A): Signal[A] = new MapSignal[T, A](source)(y => y.map(f))

    /**
     * Creates a new Rx which debounces the old Rx; updates coming in within `interval`
     * of a previous update get ignored. After the `interval` has passed, the last
     * un-applied update (if any) will be applied to update the value of the Rx
     */
    /*def debounce(interval: FiniteDuration, delay: FiniteDuration = 0 seconds)
                (implicit system: ActorSystem, ex: ExecutionContext, p: Propagator): Rx[T] = {

      if (delay == 0.seconds) new ImmediateDebouncedSignal[T](source, interval)
      else new DelayedRebounceSignal[T](source, interval, delay)
    }*/

    def filterSig(predicate: (Try[T], Try[T]) => Boolean): Signal[T] = {
      new FilterSignal(source)((x, y) => if (predicate(x, y)) y else x)
    }

  }
  implicit class pimpedFutureSignal[T](source: Signal[Future[T]]){
    /**
     * Flattens out an Rx[Future[T]] into a Rx[T]. If the first
     * Future has not yet arrived, the AsyncSig contains its default value.
     * Afterwards, it updates itself when and with whatever the Futures complete
     * with.
     *
     * `async` can be configured with a variety of Targets, to configure
     * its handling of Futures which complete out of order (RunAlways, DiscardLate)
     */
    def async[P](default: T,
                 target: AsyncSignals.Target[T] = AsyncSignals.RunAlways[T]())
                (implicit executor: ExecutionContext, p: Propagator[P]): Rx[T] = {
      new AsyncSig(default, source, target)
    }
  }



}

