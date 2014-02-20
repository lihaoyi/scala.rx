package rx
import acyclic.file
import scala.concurrent.{ExecutionContext, Future}
import rx.core.Propagator
import scala.util.{Try, Failure, Success}
import scala.concurrent.duration.FiniteDuration

package object ops {
  import acyclic.pkg
  /**
   * Extends an `Rx[Future[T]]` to allow you to flatten it into an `Rx[T]` via
   * the `.async()` method
   */
  implicit class AsyncRxOps[T](val source: Rx[Future[T]]) extends AnyVal{
    /**
     * Flattens out an Rx[Future[T]] into a Rx[T]. If the first
     * Future has not yet arrived, the Async contains its default value.
     * Afterwards, it updates itself when and with whatever the Futures complete
     * with.
     *
     * @param default The initial value of this [[Rx]] before any `Future` has completed.
     * @param discardLate Whether or not to discard the result of `Future`s which complete "late":
     *                    meaning it was created earlier but completed later than some other `Future`.
     */
    def async[P](default: T,
                 discardLate: Boolean = true)
                (implicit executor: ExecutionContext, p: Propagator[P]): Rx[T] = {
      new Async(default, source, discardLate)

    }
  }
  implicit class RxOps[+T](val source: Rx[T]) extends AnyVal{

    /**
     * Causes the given `callback` to run every time this [[Rx]]'s value is
     * changed. Returns the [[Obs]] which executes this `callback`, which should
     * generally be assigned to a variable to avoid it from being
     * garbage-collected.
     */
    def foreach(callback: T => Unit, skipInitial: Boolean = false): Obs = {
      Obs(source, "Foreach " + source.name, skipInitial){callback(source())}
    }

    /**
     * Creates a new [[Rx]] which ignores Failure conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value
     */
    def skipFailures: Rx[T] = filterAll[T](x => x.isSuccess)


    /**
     * Creates a new [[Rx]] which contains the value of the old Rx, except transformed by some
     * function.
     */
    def map[A](f: T => A): Rx[A] = new Mapper[T, A](source)(y => y.map(f))

    /**
     * Creates a new [[Rx]] which ignores specific Success conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value if the new value fails the filter. Optionally takes a `failurePred`, allowing
     * it to filter the Failure conditions as well.
     */
    def filter(successPred: T => Boolean): Rx[T] = {
      new Reducer(source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) => Failure(thrown)
          case (old, _) => old
        }
      )
    }

    /**
     * Creates a new [[Rx]] which combines the values of the source [[Rx]] according
     * to the given `combiner` function. Failures are passed through directly,
     * and transitioning from a Failure to a Success(s) re-starts the combining
     * using the result `s` of the Success.
     */
    def reduce[A >: T](combiner: (A, A) => A): Rx[A] = {
      new Reducer[A](source)(
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
     * transformer rather than just the `T`.
     */
    def mapAll[A](f: Try[T] => Try[A]): Rx[A] = new Mapper[T, A](source)(f)

    /**
     * Identical to `filter()`, except the entire `Try[T]` is available to your
     * predicate rather than just the `T`.
     */
    def filterAll[A >: T](predicate: Try[A] => Boolean): Rx[A] = new Reducer[A](source)((x, y) => if (predicate(y)) y else x)

    /**
     * Identical to `reduce()`, except both `Try[T]`s are available to your combiner,
     * rather than just the `T`s.
     */
    def reduceAll[A >: T](combiner: (Try[A], Try[A]) => Try[A]): Rx[A] = new Reducer[A](source)(combiner)

    /**
     * Creates a new [[Rx]] which debounces the original old Rx; updates coming
     * in within `interval` of a previous update get ignored. After the `interval`
     * has passed, the last un-applied update (if any) will be applied to update
     * the value of the Rx
     */
    def debounce(interval: FiniteDuration)
                (implicit scheduler: Scheduler, ex: ExecutionContext): Rx[T] = {
      new Debounce(source, interval)
    }

    /**
     * Creates a new [[Rx]] which delays the original Rx; updates to the original
     * [[Rx]] get delayed by `delay` seconds before continuing the propagation.
     */
    def delay(delay: FiniteDuration)
             (implicit scheduler: Scheduler, ex: ExecutionContext): Rx[T] = {
      new Delay(source, delay)
    }
  }

}
