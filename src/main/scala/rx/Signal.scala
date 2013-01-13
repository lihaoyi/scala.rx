package rx

import util.Try

/**
 * A Signal is a Val that can change over time, emitting pings whenever it
 * changes. It can be thought of as an extension of Futures: a Future
 * can only be completed once, and emit one event. A Signal, while also
 * possibly starting-off un-set, can be set as many times as you want,
 * generating an arbitrary number of events.
 *
 * @tparam T The type of the future this signal contains
 */
trait Signal[+T] extends Flow.Emitter[T]{
  def currentValue: T

  def now: T = currentValue

  def apply(): T = {
    val current = Sig.enclosing.value

    if (current != null){
      this.linkChild(Sig.enclosingR.value)
      Sig.enclosing.value = current.copy(
        level = math.max(this.level + 1, current.level),
        parents = this +: current.parents
      )
    }
    currentValue
  }

  def toTry: Try[T]
}

