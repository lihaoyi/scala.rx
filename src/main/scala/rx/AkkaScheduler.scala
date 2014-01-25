package rx

import akka.actor.ActorSystem
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.ExecutionContext
import rx.ops.Scheduler

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
