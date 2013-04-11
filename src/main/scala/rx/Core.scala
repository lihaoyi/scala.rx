package rx

import util.{Try, Success}

import concurrent.Future
import java.util.concurrent.atomic.AtomicReference

object Rx{

  /**
   * Creates an [[Rx]] that is defined relative to other [[Rx]]s, and
   * updates automatically when they change.
   *
   * @param calc The method of calculating the value of this [[Rx]]. This
   *             expression should be pure, as it may be evaluated multiple
   *             times redundantly.
   * @param name The name of this [[Rx]]
   * @param default The default value for this [[Rx]]
   * @tparam T The type of the value this [[Rx]] contains
   */
  def apply[T](calc: => T, name: String = ""): Rx[T] = {
    new rx.Dynamic(() => calc, name)
  }
}

/**
 * An [[Rx]] is a value that can change over time, emitting pings whenever it
 * changes to notify any dependent [[Rx]]s that they need to update.
 *
 */
trait Rx[+T] extends Emitter[T] with RxMethods[T]{

  protected[this] def currentValue: T = toTry.get

  /**
   * Identical to `apply()`, except that it does not create a dependency if
   * called within the body of another [[Rx]]
   *
   *@return The current value of this [[Rx]]
   */
  def now: T = currentValue

  /**
   * Returns current value of this [[Rx]]. If this is called within the body of
   * another [[Rx]], this will create a dependency between the two [[Rx]]s. If
   * this [[Rx]] contains an exception, that exception gets thrown.
   *
   * @return The current value of this [[Rx]]
   */
  def apply(): T = {

    Dynamic.enclosing.value = Dynamic.enclosing.value match{
      case Some((enclosing, dependees)) =>
        this.linkChild(enclosing)
        Some((enclosing, this :: dependees))
      case None => None
    }
    currentValue
  }

  def propagate[P: Propagator]() = {
    Propagator().propagate(this.getChildren.map(this -> _))
  }

  /**
   * Returns the current value stored within this [[Rx]] as a `Try`
   */
  def toTry: Try[T]
}


object Var {
  /**
   * Convenience method for creating a new [[Var]].
   */
  def apply[T](value: => T, name: String = "") = {
    new Var(value, name)
  }
}

/**
 * A [[Var]] is an [[Rx]] which can be changed manually via assignment.
 *
 * @param initValue The initial future of the Var
 */
class Var[T](initValue: => T, val name: String = "") extends Rx[T]{

  private val state = new AtomicReference(Try(initValue))
  def update[P: Propagator](newValue: => T): P = {
    state.set(Try(newValue))
    propagate()
  }

  protected[rx] def level = 0
  def toTry = state.get()
}

object Obs{
  /**
   * Convenience method for creating a new [[Obs]].
   */
  def apply[T](es: Emitter[Any], name: String = "", skipInitial: Boolean = false)(callback: => Unit) = {
    new Obs(es, () => callback, name, skipInitial)
  }
}

/**
 * An [[Obs]] is something that produces side-effects when the source [[Rx]]
 * changes. An [[Obs]] is always run right at the end of every propagation wave,
 * ensuring it is only called once per wave (in contrast with [[Rx]]s, which
 * may update multiple times before settling).
 *
 * @param callback a callback to run when this Obs is pinged
 */
class Obs(source: Emitter[Any],
          callback: () => Unit,
          val name: String = "",
          skipInitial: Boolean = false)
          extends Reactor[Any]{
  /**
   * A flag variable which can be turned off to prevent the [[Obs]] from
   * triggering its `callback`.
   */
  @volatile var active = true

  source.linkChild(this)
  def getParents = Seq(source)
  protected[rx] def level = Long.MaxValue

  def ping[P: Propagator](incoming: Seq[Emitter[Any]]) = {
    if (active && getParents.intersect(incoming).isDefinedAt(0)){
      callback()
    }
    Nil

  }

  /**
   * Manually trigger this observer, causing its callback to run.
   */
  def trigger() = {
    this.ping(this.getParents)(Propagator.Immediate)
  }

  if (!skipInitial) trigger()
}
