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
    def skipFailures: Signal[T] = filterAll[T](x => x.isSuccess)


    /**
     * Creates a new Rx which contains the value of the old Rx, except transformed by some
     * function.
     */
    def map[A](f: T => A): Signal[A] = new MapSignal[T, A](source)(y => y.map(f))

    /**
     * Creates a new Rx which ignores specific Success conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value if the new value fails the filter. Optionally takes a failurePred, allowing
     * it to filter the Failure conditions as well.
     */
    def filter(successPred: T => Boolean): Signal[T] = {
      new ReduceSignal(source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) => Failure(thrown)
          case (old, _) => old
        }
      )
    }

    /**
     * Creates a new Rx which combines the values of the source Rx according
     * to the given `combiner` function. Failures are passed through directly,
     * and transitioning from a Failure to a Success(s) re-starts the combining
     * using the result `s` of the Success.
     */
    def reduce[A >: T](combiner: (A, A) => A): Signal[A] = {
      new ReduceSignal[A](source)(
        (x, y) => (x, y) match{
          case (Success(a), Success(b)) => Success(combiner(a, b))
          case (Failure(_), Success(b)) => Success(b)
          case (Success(_), Failure(b)) => Failure(b)
          case (Failure(_), Failure(b)) => Failure(b)
        }
      )
    }

    /**
     * Identical to map(), except the entire `Try[T]` is available to your
     * transformer rather than just the `T`
     */
    def mapAll[A](f: Try[T] => Try[A]): Signal[A] = new MapSignal[T, A](source)(f)

    /**
     * Identical to `filter()`, except the entire `Try[T]` is available to your
     * predicate rather than just the `T`
     */
    def filterAll[A >: T](predicate: Try[A] => Boolean) = new ReduceSignal[A](source)((x, y) => if (predicate(y)) y else x)

    /**
     * Identical to `reduce()`, except both `Try[T]`s are available to your combiner,
     * rather than just the `T`s.
     */
    def reduceAll[A >: T](combiner: (Try[A], Try[A]) => Try[A]) = new ReduceSignal[A](source)(combiner)

    /**
     * Creates a new Rx which debounces the old Rx; updates coming in within `interval`
     * of a previous update get ignored. After the `interval` has passed, the last
     * un-applied update (if any) will be applied to update the value of the Rx
     */
    def debounce(interval: FiniteDuration)
                (implicit system: ActorSystem, ex: ExecutionContext): Rx[T] = {
      new DebouncedSignal(source, interval)
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
                 discardLate: Boolean = true)
                (implicit executor: ExecutionContext, p: Propagator[P]): Rx[T] = {
      new AsyncSig(default, source, discardLate)
    }
  }



}

