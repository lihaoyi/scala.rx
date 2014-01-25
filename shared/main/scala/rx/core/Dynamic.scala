package rx.core

import util.{DynamicVariable, Try}



import concurrent.duration._


import rx.ops.Spinlock

import scala.Some


object Dynamic{
  private[rx] val enclosing = new DynamicVariable[Option[(Dynamic[Any], List[Rx[Any]])]](None)
}

/**
 * An `Rx` whose dependencies are set dynamically at runtime when `calc` is
 * evaluated, and may change again any time it gets re-evaluated
 */
class Dynamic[+T](calc: () => T,
                  val name: String = "")
                  extends Rx[T]
                  with Reactor[Any]
                  with Spinlock[T]{

  protected[this] class State(val parents: Seq[Emitter[Any]],
                              val level: Long,
                              timestamp: Long,
                              value: Try[T])
                              extends SpinState(timestamp, value)

  type StateType = State

  protected[this] val state = SpinSet(makeState)

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

  def parents = state().parents

  override def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]] = {
    if (parents.intersect(incoming).isDefinedAt(0)){
      super.ping(incoming)
    } else Nil
  }

  protected[rx] def level = state().level
}





