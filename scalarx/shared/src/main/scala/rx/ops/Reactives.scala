/**
 * Useful Rxs which generally should not be publicaly usable
 */
package rx
package ops
import acyclic.file
import java.util.concurrent.atomic.AtomicLong
import scala.util.Try

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
 * Calculated value Rx[V] that statically depends on two values Rx[T], Rx[U].
 */
private[rx] class Zip[T, U, V](firstSource: Rx[T], secondSource: Rx[U])
                               (transformer: (Try[T], Try[U]) => Try[V])
                               extends Spinlock[V]{
  override def level: Long = math.max(firstSource.level, secondSource.level) + 1
  override def name: String = "Join"
  override val parents: Set[Emitter[_]] = Set(firstSource, secondSource)
  parents.foreach(_.linkChild(this))

  protected[this] type StateType = SpinState
  def makeState = new SpinState(
    getStamp,
    transformer(firstSource.toTry, secondSource.toTry)
  )

  protected[this] val state = SpinSet(makeState)
}
