package rx.async

import scala.concurrent.duration._

trait SyncScheduler extends Scheduler {
  override def schedule(interval: FiniteDuration, r: Runnable): Cancelable

  override def scheduleOnce(r: Runnable): Cancelable = {
    r.run()
    Cancelable()
  }

  override def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable
}
