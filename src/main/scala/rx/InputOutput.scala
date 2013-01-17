package rx

import util.{Try, Success}
import java.util.concurrent.atomic.AtomicReference
import Flow.Settable


object Var {
  def apply[T](value: T)(implicit name: String = "") = {
    new Var(name, value)
  }
}

/**
 * A Var is a Signal which can be changed manually via assignment.
 *
 * @param initValue The initial future of the Var
 * @tparam T The type of the future this Var contains
 */
case class Var[T](name: String, val initValue: T) extends Settable[T](initValue){
  def update(newValue: Try[T]) = updateS(newValue)
  def update(newValue: T) = updateS(newValue)
  def update(calc: T => T) = updateS(calc)

}


object Obs{
  def apply[T](es: Flow.Emitter[Any])(callback: => Unit) = {
    new Obs("", es)(() => callback)
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
