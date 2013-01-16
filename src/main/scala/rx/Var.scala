package rx

import util.{Try, Success}
import java.util.concurrent.atomic.AtomicReference
import Flow.Settable


/**
 * Contains a future that can be updated manually, triggering his children.
 */
object Var {
  def apply[T](value: T)(implicit name: String = "") = {
    new Var(name, value)
  }
}

/**
 * A Var is a Signal which can be changed manually via assignment. Setting
 * the future is thread-safe as the semantics are controlled by the `ready`
 * AtomicBoolean.
 *
 * @param initValue The initial future of the Var
 * @tparam T The type of the future this Var contains
 */
case class Var[T](name: String, val initValue: T) extends Settable[T](initValue){
  override def update(newValue: Try[T]) = super.update(newValue)
  override def update(newValue: T) = super.update(newValue)
  override def update(calc: T => T) = super.update(calc)
}


object Obs{
  def apply[T](es: Flow.Emitter[Any])(callback: => Unit) = {
    new Obs("", es)(() => callback)
  }
  def cfg[T](name: String)(es: Flow.Emitter[Any])(callback: => Unit) = {
    new Obs(name, es)(() => callback)
  }
}

/**
 * An Obs is something that reacts to pings and performs side effects.
 *

 * @param callback a callback to run when this Obs is pinged
 */
case class Obs(name: String, source: Flow.Emitter[Any])(callback: () => Unit) extends Flow.Reactor[Any]{
  @volatile var active = true
  source.linkChild(this)
  def getParents = Seq(source)
  def level = Long.MaxValue

  def ping(incoming: Seq[Flow.Emitter[Any]]) = {

    if (active && getParents.map(_.getEmitter).intersect(incoming.map(_.getEmitter)).isDefinedAt(0)){
      util.Try(callback())
    }
    Nil
  }
  def trigger() = {
    this.ping(this.getParents)
  }
}
