/**
 * Useful Rxs which generally should not be publicaly usable
 */
package rx

import java.util.concurrent.atomic.AtomicLong
import scala.util.Try

import scala.Some


/**
 * Signals whose state contains an auto-incrementing "timestamp" in order to
 * reject out of order completions
 */
private[rx] trait Incrementing[+T] extends Rx[T]{
  private val updateCount = new AtomicLong(0)
  def getStamp = updateCount.getAndIncrement

  class SpinState(val timestamp: Long,
                  val value: Try[T])

  type StateType <: SpinState
  protected[this] val state: Atomic[StateType]
  def toTry = state().value

}
private[rx] trait Spinlock[+T] extends Incrementing[T]{

  def makeState: StateType

  def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]] = {

    val newState = makeState
    val set = state.spinSetOpt{oldState =>
      if (newState.value != oldState.value
        && newState.timestamp >= oldState.timestamp){
        Some(newState)
      }else{
        None
      }
    }
    if(set) this.getChildren
    else Nil
  }
}

private[rx] abstract class Wrapper[T, +A](source: Rx[T], prefix: String)
  extends Rx[A]
  with Reactor[Any]{
  source.linkChild(this)
  def level = source.level + 1
  def getParents = Seq(source)
  def name = prefix + " " + source.name
}


private class Reducer[T](source: Rx[T])
               (transformer: (Try[T], Try[T]) => Try[T])
  extends Wrapper[T, T](source, "Reduce")
  with Spinlock[T]{

  type StateType = SpinState
  protected[this] val state = Atomic(new SpinState(
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

  type StateType = SpinState
  def makeState = new SpinState(
    getStamp,
    transformer(source.toTry)
  )

  protected[this] val state = Atomic(makeState)
}