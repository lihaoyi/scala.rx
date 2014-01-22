package rx

import scala.concurrent.duration.FiniteDuration
import scala.rx.Scheduler
import scala.concurrent.ExecutionContext

class DomScheduler extends Scheduler {
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext): Unit = {
    org.scalajs.dom.setTimeout(() => thunk, interval.toMillis)
  }
}
