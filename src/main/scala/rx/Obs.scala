package rx

import scala.concurrent.Promise

object Obs{
  def apply[T](es: Emitter[Any]*)(callback: => Unit){
    new Obs(es.toSeq)(() => callback)
  }
}

/**
 * An Obs is something that reacts to pings and performs side effects.
 *
 * @param es a list of emitters for this Obs to react to
 * @param callback a callback to run when this Obs is pinged
 */
class Obs(es: Seq[Emitter[Any]])(callback: () => Unit) extends Reactor[Any]{

  var parents: Seq[Emitter[Any]] = es
  es.foreach(_.linkChild(this))
  def getParents = parents
  def level = Long.MaxValue

  def ping(e: Seq[Emitter[_]]) = {
    callback()
    Seq() -> false
  }
}
