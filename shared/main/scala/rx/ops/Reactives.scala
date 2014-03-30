/**
 * Useful Rxs which generally should not be publicaly usable
 */
package rx
package ops
import acyclic.file
import java.util.concurrent.atomic.{AtomicReference, AtomicLong}
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
 * Zipper class that
 * @param source source reactive
 * @param transformer transforming function
 * @tparam T incoming type
 * @tparam A outgoing type
 */
private class Zipper[T,+A](source: Rx[T])
                        (transformer: (Try[T], Try[T]) => Try[A])
  extends Wrapper[T, A](source, "Zip")
  with Spinlock[A]{

  protected[this] type StateType = SpinState

  /**
   * Previous value
   */
  private lazy val prev= new AtomicReference[Try[T]](source.toTry)

  override def ping[P: Propagator](incoming: Set[Emitter[_]]): Set[Reactor[_]] =
    if(prev.get()==source.toTry) Set() else  super.ping[P](incoming)


  protected[this] val state = SpinSet(makeState)

  def makeState = {
    val pre = prev.get()
    prev.set(source.toTry)
    new SpinState( getStamp, transformer(pre, source.toTry)  )  }

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