package rx

import scala.concurrent.duration.FiniteDuration

import scala.concurrent.ExecutionContext

class DomScheduler extends rx.ops.Scheduler {
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext): Unit = {
    org.scalajs.dom.setTimeout(() => thunk, interval.toMillis)
  }
}

