package rx

import util.{Try, Success}
import java.util.concurrent.atomic.AtomicReference
import akka.agent.Agent
import concurrent.Future


object Var {
  def apply[T](value: => T)(implicit p: Propagator) = {
    new Var(value)
  }
  def apply[T](x: =>Nothing = ???, name: String = "")(value: => T)(implicit p: Propagator) = {
    new Var(value, name)
  }
}

/**
 * A Var is a Signal which can be changed manually via assignment.
 *
 * @param initValue The initial future of the Var
 * @tparam T The type of the future this Var contains
 */
class Var[T](initValue: => T, val name: String = "")(implicit p: Propagator) extends Flow.Signal[T]{
  import p.executionContext
  val state = Agent(Try(initValue))
  def update(newValue: => T): Future[Unit] = for {
    altered <- state.alter(Try(newValue))
    done <- propagate()
  } yield done

  def level = 0
  def toTry = state()
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
case class Obs(source: Seq[Flow.Emitter[Any]], callback: () => Unit, name: String = "")extends Flow.Reactor[Any]{
  @volatile var active = true
  source.foreach(_.linkChild(this))
  def getParents = source
  def level = Long.MaxValue

  def ping(incoming: Seq[Flow.Emitter[Any]]) = {
    if (active && getParents.intersect(incoming).isDefinedAt(0)){
      Future.successful(util.Try(callback()))
    }
    Future.successful(Nil)
  }
  def trigger() = {
    this.ping(this.getParents)
  }
}
