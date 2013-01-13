package rx

object Obs{
  def apply[T](es: Emitter[Any]*)(callback: => Unit) = {
    new Obs("", es.toSeq)(() => callback)
  }
  def cfg[T](name: String)(es: Emitter[Any]*)(callback: => Unit) = {
    new Obs(name, es.toSeq)(() => callback)
  }
}

/**
 * An Obs is something that reacts to pings and performs side effects.
 *
 * @param es a list of emitters for this Obs to react to
 * @param callback a callback to run when this Obs is pinged
 */
case class Obs(name: String, es: Seq[Emitter[Any]])(callback: () => Unit) extends Reactor[Any]{

  var parents: Seq[Emitter[Any]] = es
  @volatile var active = true
  es.foreach(_.linkChild(this))
  def getParents = parents
  def level = Long.MaxValue

  def ping(incoming: Seq[Emitter[Any]]) = {
    if (active && getParents.intersect(incoming).isDefinedAt(0)){
      util.Try(callback())
    }
    Nil
  }
}
