package rx.core

import util.Try

import java.util.concurrent.atomic.AtomicReference
import rx.core

object Rx{

  /**
   * This guys sole purpose is to help implement a "keyword-only" argument
   * list in Rx.apply
   */
  object Cookie
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
  def apply[T](calc: => T): Rx[T] = {
    new Dynamic(() => calc)
  }

  def apply[T](c: Cookie.type = Cookie,
               name: String = "")
              (calc: => T): Rx[T] = {
    new core.Dynamic(() => calc, name)
  }
}

/**
 * An [[Rx]] is a value that can change over time, emitting pings whenever it
 * changes to notify any dependent [[Rx]]s that they need to update.
 *
 */
trait Rx[+T] extends Emitter[T] with Reactor[Any]{

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
    Propagator().propagate(this.children.map(this -> _))
  }

  /**
   * Returns the current value stored within this [[Rx]] as a `Try`
   */
  def toTry: Try[T]

  /**
   * descndents
   * descendants
   * ancestors
   * Shorthand to call `.kill()` on this [[Rx]] as well as any of its
   */
  def killAll(): Unit = {
    this.kill()
    descendants.foreach(_.kill())
  }

  /**
   * Forces this [[Rx]] to recalculate its value. This typically does
   * nothing unless the [[Rx]] is a [[Dynamic]], since only [[Dynamic]]s
   * can have their calculated value change without their ancestors
   * pinging them.
   */
    def recalc[P: Propagator](): P = Propagator().propagate(Set(this -> this))
}

class Staged[T](val v: Var[T], t: T){
  def commit() = v.updateSilent(t)
}

object Var {
  /**
   * Convenience method for creating a new [[Var]].
   */
  def apply[T](value: => T, name: String = "") = {
    new Var(value, name)
  }
  def set[P: Propagator](thingies: Staged[_]*) = {
    thingies.foreach(_.commit())

    Propagator().propagate(
      thingies.flatMap{ v =>
        v.v.children
           .map(v.v.asInstanceOf[Emitter[_]] -> _)
      }.toSet
    )
  }
}

/**
 * A [[Var]] is an [[Rx]] which can be changed manually via assignment.
 *
 * @param initValue The initial future of the Var
 */
class Var[T](initValue: => T, val name: String = "") extends Rx[T]{

  private val state = new AtomicReference(Try(initValue))

  /**
   * Updates the value in this `Var` and propagates the change through to its
   * children and descendents
   */
  def update[P: Propagator](newValue: => T): P = {
    updateSilent(newValue)
    propagate()
  }

  /**
   * Updates the value in this `Var` *without* propagating the change through
   * to its children and descendents
   */
  def updateSilent(newValue: => T) = {
    state.set(Try(newValue))
  }
  def level = 0

  def toTry = state.get()
  def parents: Set[Emitter[Any]] = Set.empty

  def ping[P: Propagator](incoming: Set[Emitter[_]]) = {
    this.children
  }
}
object Obs{
  /**
   * Convenience method for creating a new [[Obs]].
   */
  def apply[T](es: Emitter[Any], name: String = "", skipInitial: Boolean = false)
              (callback: => Unit) = {
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

  source.linkChild(this)

  def parents = Set(source)

  def level = Long.MaxValue

  def ping[P: Propagator](incoming: Set[Emitter[_]]) = {
    if (!parents.intersect(incoming).isEmpty){
      callback()
    }
    Set.empty
  }

  /**
   * Manually trigger this observer, causing its callback to run.
   */
  def trigger() = {
    this.ping(this.parents)(Propagator.Immediate)
  }

  if (!skipInitial) trigger()
}
