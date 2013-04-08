package rx

import util.{Try, Success}

import concurrent.Future
import java.util.concurrent.atomic.AtomicReference


object Var {
  def apply[T, P: Propagator](value: => T, name: String = "") = {
    new Var(value, name)
  }
}

/**
 * A Var is a Signal which can be changed manually via assignment.
 *
 * @param initValue The initial future of the Var
 * @tparam T The type of the future this Var contains
 */
class Var[T, P: Propagator](initValue: => T, val name: String = "") extends Flow.Signal[T]{

  val state = new AtomicReference(Try(initValue))
  def update(newValue: => T): P = {
    state.set(Try(newValue))
    propagate()
  }

  def level = 0
  def toTry = state.get()
}

object Obs{
  def apply[T](es: Flow.Emitter[Any]*)(callback: => Unit) = {
    new Obs(es, () => callback)
  }
  def apply[T](x: =>Nothing = ???, name: String = "")(es: Flow.Emitter[Any]*)(callback: => Unit) = {
    new Obs(es, () => callback, name)
  }
}

/**
 * An Obs is something that produces side-effects when the source Signal
 * changes. an Obs is always run right at the end of every propagation cycle,
 * ensuring it is only called once per cycle (in contrast with Signal[T]s, which
 * may update multiple times before settling)
 *
 * @param callback a callback to run when this Obs is pinged
 */
case class Obs(source: Seq[Flow.Emitter[Any]], callback: () => Unit, name: String = "") extends Flow.Reactor[Any]{
  @volatile var active = true

  source.foreach(_.linkChild(this))
  def getParents = source
  def level = Long.MaxValue

  def ping(incoming: Seq[Flow.Emitter[Any]]) = {
    if (active && getParents.intersect(incoming).isDefinedAt(0)){
      callback()
    }
    Nil

  }
  def trigger() = {
    this.ping(this.getParents)
  }
}
