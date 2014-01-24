package rx
package ops

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

trait Scheduler{
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext)
}
