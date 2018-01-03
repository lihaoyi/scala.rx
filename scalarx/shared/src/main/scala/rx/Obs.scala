package rx

/**
  * Wraps a simple callback, created by `trigger`, that fires when that
  * [[Rx]] changes.
  */
class Obs(val thunk: () => Unit, upstream: Rx[_]) {

  var dead = false

  /**
    * Stop this observer from triggering and allow it to be garbage-collected
    */
  def kill(): Unit = {
    upstream.observers.remove(this)
    dead = true
  }
}

