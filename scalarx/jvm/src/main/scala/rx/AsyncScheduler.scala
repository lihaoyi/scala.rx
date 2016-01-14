package rx

import java.util.concurrent.{FutureTask, ScheduledExecutorService, TimeUnit}

import rx.async.{Scheduler, Cancelable}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AsyncScheduler(
    s: => ScheduledExecutorService,
    ec: ExecutionContext) extends Scheduler {

  /** Lazy val because not needed for [[scheduleOnce]] without duration */
  lazy val executorService = s

  def schedule(interval: Long, unit: TimeUnit, r: Runnable): Cancelable = {
    require(interval > 0)
    val initialDelay = interval
    val task = executorService.scheduleAtFixedRate(r, initialDelay, interval, unit)
    Cancelable(task.cancel(true))
  }

  def scheduleOnce(initialDelay: Long,
    unit: TimeUnit,
    r: Runnable
    ): Cancelable = {
    require(initialDelay > 0)
    val task = executorService.schedule(r, initialDelay, unit)
    Cancelable(task.cancel(true))
  }

  override def schedule(interval: FiniteDuration, r: Runnable): Cancelable =
    schedule(interval.length, interval.unit, r)

  override def scheduleOnce(r: Runnable): Cancelable = {
    val task = new FutureTask(r, null)
    ec.execute(task)
    Cancelable(task.cancel(true))
  }

  override def scheduleOnce(initialDelay: FiniteDuration, r: Runnable): Cancelable =
    scheduleOnce(initialDelay.length, initialDelay.unit, r)
}
