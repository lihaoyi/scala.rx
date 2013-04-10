package rx

import scala.Predef._
import scala.util.{Try, Failure, Success}
import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorSystem
import scala.concurrent.{Future, ExecutionContext}

/**
 * All sorts of useful things which should be not be used externally
 */
package object utility {
  trait Node{
    def level: Long
    def name: String
    val id: String = util.Random.alphanumeric.head.toString

    def debug(s: String) {
      println(id + ": " + s)
    }
  }
  trait SignalMethods[+T]{ source: Signal[T] =>

  /**
   * Creates a new Signal which ignores Failure conditions of the source Signal; it
   * will not propagate the changes, and simply remain holding on to its last
   * value
   */
    def skipFailures: Signal[T] = filterAll[T](x => x.isSuccess)


    /**
     * Creates a new Signal which contains the value of the old Signal, except transformed by some
     * function.
     */
    def map[A](f: T => A): Signal[A] = new Map[T, A](source)(y => y.map(f))

    /**
     * Creates a new Signal which ignores specific Success conditions of the source Signal; it
     * will not propagate the changes, and simply remain holding on to its last
     * value if the new value fails the filter. Optionally takes a failurePred, allowing
     * it to filter the Failure conditions as well.
     */
    def filter(successPred: T => Boolean): Signal[T] = {
      new Reduce(source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) => Failure(thrown)
          case (old, _) => old
        }
      )
    }

    /**
     * Creates a new Signal which combines the values of the source Signal according
     * to the given `combiner` function. Failures are passed through directly,
     * and transitioning from a Failure to a Success(s) re-starts the combining
     * using the result `s` of the Success.
     */
    def reduce[A >: T](combiner: (A, A) => A): Signal[A] = {
      new Reduce[A](source)(
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
    def mapAll[A](f: Try[T] => Try[A]): Signal[A] = new Map[T, A](source)(f)

    /**
     * Identical to `filter()`, except the entire `Try[T]` is available to your
     * predicate rather than just the `T`
     */
    def filterAll[A >: T](predicate: Try[A] => Boolean) = new Reduce[A](source)((x, y) => if (predicate(y)) y else x)

    /**
     * Identical to `reduce()`, except both `Try[T]`s are available to your combiner,
     * rather than just the `T`s.
     */
    def reduceAll[A >: T](combiner: (Try[A], Try[A]) => Try[A]) = new Reduce[A](source)(combiner)

    /**
     * Creates a new Signal which debounces the old Signal; updates coming in within `interval`
     * of a previous update get ignored. After the `interval` has passed, the last
     * un-applied update (if any) will be applied to update the value of the Signal
     */
    def debounce(interval: FiniteDuration)
                (implicit system: ActorSystem, ex: ExecutionContext): Signal[T] = {
      new Debounce(source, interval)
    }

    /**
     * Creates a new Signal which debounces the old Signal; updates coming in within `interval`
     * of a previous update get ignored. After the `interval` has passed, the last
     * un-applied update (if any) will be applied to update the value of the Signal
     */
    def delay(delay: FiniteDuration)
             (implicit system: ActorSystem, ex: ExecutionContext): Signal[T] = {
      new Delay(source, delay)
    }

  }
  implicit class pimpedFutureSignal[T](source: Signal[Future[T]]){
    /**
     * Flattens out an Signal[Future[T]] into a Signal[T]. If the first
     * Future has not yet arrived, the Async contains its default value.
     * Afterwards, it updates itself when and with whatever the Futures complete
     * with.
     *
     * `async` can be configured with a variety of Targets, to configure
     * its handling of Futures which complete out of order (RunAlways, DiscardLate)
     */
    def async[P](default: T,
                 discardLate: Boolean = true)
                (implicit executor: ExecutionContext, p: Propagator[P]): Signal[T] = {
      new Async(default, source, discardLate)
    }
  }

}
