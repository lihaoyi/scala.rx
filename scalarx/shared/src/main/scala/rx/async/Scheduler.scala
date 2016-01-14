package rx.async

import scala.concurrent.duration.FiniteDuration

/**
 * Inspired from Monifu's scheduling code.
 */
trait Scheduler {
  def schedule(interval: FiniteDuration, r: Runnable): Cancelable
  def scheduleOnce(action: Runnable): Cancelable
  def scheduleOnce(initialDelay: FiniteDuration, action: Runnable): Cancelable

  def schedule(interval: FiniteDuration)(action: => Unit): Cancelable =
    schedule(interval, new Runnable {
      def run(): Unit = action
    })

  def scheduleOnce(initialDelay: FiniteDuration)(action: => Unit): Cancelable =
    scheduleOnce(initialDelay, new Runnable {
      def run(): Unit = action
    })

  def currentTimeMillis(): Long = System.currentTimeMillis()
}

//trait DefaultScheduler {
//  implicit val scheduler = new SyncScheduler
//}