package rx

import scala.concurrent.duration.FiniteDuration

import scala.concurrent.ExecutionContext

/**
 * A [[Scheduler]] that wraps the DOM's `setTimeout` function
 */
class DomScheduler extends rx.ops.Scheduler {
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext): Unit = {
    org.scalajs.dom.setTimeout(() => thunk, interval.toMillis)
  }
}

