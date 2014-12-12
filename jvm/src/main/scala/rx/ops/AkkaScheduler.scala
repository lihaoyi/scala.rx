package rx
package ops

import akka.actor.ActorSystem
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

/**
 * A [[Scheduler]] that wraps an `ActorSystem`'s scheduler.
 */
class AkkaScheduler(system: ActorSystem) extends Scheduler {
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext): Unit = {
    system.scheduler.scheduleOnce(interval)(thunk)
  }
}
