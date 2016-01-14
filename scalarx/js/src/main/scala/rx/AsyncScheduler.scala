package rx

import rx.async.{Scheduler, Cancelable}

import scala.concurrent.duration._

import scala.scalajs.js

class AsyncScheduler extends Scheduler {
  type Timeout = js.Dynamic
  type Interval = js.Dynamic

  def setTimeout(delayMillis: Long, r: Runnable): Timeout = {
    val lambda: js.Function = () => r.run()
    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  def setInterval(intervalMillis: Long, r: Runnable): Interval = {
    val lambda: js.Function = () => r.run()
    js.Dynamic.global.setInterval(lambda, intervalMillis)
  }

  def clearTimeout(task: Timeout): Unit =
    js.Dynamic.global.clearTimeout(task)

  def clearInterval(task: Interval): Unit =
    js.Dynamic.global.clearInterval(task)

  override def scheduleOnce(r: Runnable): Cancelable = {
    r.run()
    Cancelable()
  }

  override def schedule(interval: FiniteDuration, r: Runnable): Cancelable = {
    val task = setInterval(interval.toMillis, r)
    Cancelable(clearInterval(task))
  }

  override def scheduleOnce(initialDelay: FiniteDuration,
    r: Runnable
    ): Cancelable = {
    val task = setTimeout(initialDelay.toMillis, r)
    Cancelable(clearTimeout(task))
  }
}
