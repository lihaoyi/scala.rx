package rx.async

trait Cancelable {
  def cancel(): Boolean
}

object Cancelable {
  def apply(): Cancelable = apply({})

  def apply(callback: => Unit): Cancelable = new Cancelable {
    var isCanceled = false

    def cancel(): Boolean = {
      if (isCanceled) false
      else {
        isCanceled = true
        callback
        true
      }
    }
  }
}