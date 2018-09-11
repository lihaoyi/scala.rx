package rx

/**
  * Wraps a simple callback, created by `trigger`, that fires when that
  * [[Rx]] changes.
  */
class Obs(val thunk: () => Unit, upstream: Rx[_], owner: Option[Ctx.Owner]) {

  var dead = false

  /**
    * Stop this observer from triggering and allow it to be garbage-collected
    */
  def kill(): Unit = {
    owner.foreach(_.contextualRx.ownedObservers.remove(this))
    upstream.observers.remove(this)
    dead = true
  }
}

