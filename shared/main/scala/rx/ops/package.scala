package rx

import scala.concurrent.{ExecutionContext, Future}
import rx.core.Propagator

package object ops {

  /**
   * Extends an `Rx[Future[T]]` to allow you to flatten it into an `Rx[T]` via
   * the `.async()` method
   */
  implicit class AsyncRx[T](source: Rx[Future[T]]){
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
}
