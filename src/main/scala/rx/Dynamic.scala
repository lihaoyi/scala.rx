package rx


import util.{DynamicVariable, Failure, Try}
import scala.util.Success
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import annotation.tailrec

import concurrent.{Await, Future}
import concurrent.duration._
import reflect.ClassTag


object Dynamic{
  /**
   * Convenient method for constructing a new [[Dynamic]]
   */
  def apply[T, P: Propagator](calc: => T, name: String = "", default: T = null.asInstanceOf[T]) = {
    new Dynamic(() => calc, name, default)
  }

  private[rx] val enclosing = new DynamicVariable[Option[(Dynamic[Any], List[Signal[Any]])]](None)
}

/**
 * A [[Dynamic]] is a signal that is defined relative to other signals, and
 * updates automatically when they change.
 *
 * Note that while the propagation tries to minimize the number of times a
 * [[Dynamic]] needs to be recalculated, there is always going to be some
 * redundant recalculation. Since this is unpredictable, the body of a
 * [[Dynamic]] should always be side-effect free
 *
 * @param calc The method of calculating the value of this [[Dynamic]]
 * @tparam T The type of the future this contains
 */
class Dynamic[+T](calc: () => T,
                  val name: String = "",
                  default: T = null.asInstanceOf[T])
                  extends Signal[T]
                  with Reactor[Any]
                  with utility.Spinlock[T]{

  @volatile var active = true

  protected[this] class State(val parents: Seq[Emitter[Any]],
                              val level: Long,
                              timestamp: Long,
                              value: Try[T])
                              extends SpinState(timestamp, value)

  object Initial extends State(Nil, 0, 0, Success(default))
  type StateType = State

  protected[this] val state = Atomic(makeState)

  def makeState = {
    val startCalc = getStamp
    val (newValue, deps) =
      Dynamic.enclosing.withValue(Some(this -> Nil)){
        (Try(calc()), Dynamic.enclosing.value.get._2)
      }

    new State(
      deps,
      (0L :: deps.map(_.level)).max,
      startCalc,
      newValue
    )
  }

  def getParents = state().parents

  override def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]] = {
    if (active && getParents.intersect(incoming).isDefinedAt(0)){
      super.ping(incoming)
    } else Nil
  }

  def level = state().level
}





