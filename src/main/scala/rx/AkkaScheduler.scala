package rx

import akka.actor.ActorSystem
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.rx.Scheduler
import scala.concurrent.ExecutionContext

class AkkaScheduler(system: ActorSystem) extends Scheduler {
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext): Unit = {
    system.scheduler.scheduleOnce(interval)(thunk)
  }
}
