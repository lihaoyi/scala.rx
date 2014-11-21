/**
 * Useful Rxs which generally should not be publicaly usable
 */
package rx
package ops
import acyclic.file
import java.util.concurrent.atomic.AtomicLong
import scala.util.{Success, Failure, Try}

import scala.Some
import rx.core._
import rx.core.SpinSet


private[rx] abstract class Wrapper[T, +A](source: Rx[T], prefix: String)
                                          extends Rx[A]
                                          with Reactor[Any]{
  source.linkChild(this)
  def level = source.level + 1
  def parents = Set(source)
  def name = prefix + " " + source.name
}


private class Reducer[T](source: Rx[T])
                        (transformer: (Try[T], Try[T]) => Try[T])
                         extends Wrapper[T, T](source, "Reduce")
                         with Spinlock[T]{

  protected[this] type StateType = SpinState
  protected[this] val state = SpinSet(new SpinState(
    getStamp,
    source.toTry
  ))

  def makeState = new SpinState(
    getStamp,
    transformer(state().value, source.toTry)
  )
}

/**
 * A Rx[A] which is a direct transformation of another Rx[T] via a
 * transformation function. Generally created via the `.map()` method on a
 * Rx[A].
 */
private[rx] class Mapper[T, +A](source: Rx[T])
                               (transformer: Try[T] => Try[A])
                                extends Wrapper[T, A](source, "Map")
                                with Spinlock[A]{

  protected[this] type StateType = SpinState
  def makeState = new SpinState(
    getStamp,
    transformer(source.toTry)
  )

  protected[this] val state = SpinSet(makeState)
}

/**
 * A Rx[A] which 'diffs' the current and past values of another Rx[T] via a diffing function.
 * Generally created via the `.diff()` method on a Rx[T].
 */
private[rx] class Differ[T, +A](source: Rx[T])
                               (transformer: (Try[T], Try[T]) => Try[A])
                                extends Wrapper[T, A](source, "Diff")
                                with Spinlock[A]{
  protected[this] type StateType = SpinState
  protected[this] var previous: Try[T] = source.toTry
  protected[this] val state = SpinSet(new SpinState(
    getStamp,
    transformer(previous, source.toTry)
  ))

  def makeState = {
    val p = previous
    previous = source.toTry
    new SpinState(
      getStamp,
      transformer(p, source.toTry)
    )
  }
}

private[rx] class Folder[T, +A](source: Rx[T], zero: A)
                               (transformer: (Try[A], Try[T]) => Try[A])
                               extends Wrapper[T, A](source, "Diff")
                               with Spinlock[A]
{
  protected[this] type StateType = SpinState
  protected[this] val state = SpinSet(new SpinState(
    getStamp,
    transformer(Success(zero), source.toTry)
  ))

  def makeState = {
    new SpinState(
      getStamp,
      transformer(state().value, source.toTry)
    )
  }
}