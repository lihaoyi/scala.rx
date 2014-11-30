package rx.core
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.annotation.tailrec
import scala.Some
import scala.util.Try

/**
 * A wrapper around `AtomicReference`, allowing you to apply "atomic"
 * transforms to the boxed data by using a Compare-And-Set retry loop.
 */
case class SpinSet[T](t: T) extends AtomicReference[T](t){
  def apply() = get
  def update(t: T) = set(t)
  @tailrec final def spinSet(transform: T => T): Unit = {
    val oldV = this()
    val newV = transform(oldV)
    if (!compareAndSet(oldV, newV)) {
      spinSet(transform)
    }
  }
  @tailrec final def spinSetOpt(transform: T => Option[T]): Boolean = {
    val oldV = this()
    val newVOpt = transform(oldV)
    newVOpt match{
      case Some(newV) => if (!compareAndSet(oldV, newV)) {
        spinSetOpt(transform)
      } else true
      case None => false
    }

  }
}

private[rx] trait Spinlock[+T] extends Incrementing[T]{

  protected[this] def makeState: StateType

  def ping[P: Propagator](incoming: Set[Emitter[_]]): Set[Reactor[_]] = {

    val newState = makeState
    val oldValue = state().value
    state.spinSetOpt{ oldState =>
      if (newState.timestamp >= oldState.timestamp){
        Some(newState)
      }else{
        None
      }
    }
    if(state().value != oldValue) this.children
    else Set()
  }
}

/**
 * Signals whose state contains an auto-incrementing "timestamp" - order to
 * reject out of order completions
 */
private[rx] trait Incrementing[+T] extends Rx[T]{
  private val updateCount = new AtomicLong(0)
  def getStamp = updateCount.getAndIncrement

  class SpinState(val timestamp: Long, val value: Try[T])
  protected[this] type StateType <: SpinState

  protected[this] val state: SpinSet[StateType]
  def toTry = state().value

}