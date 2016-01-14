package rx

import rx.async.Scheduler

object Platform  {
  implicit lazy val DefaultScheduler: Scheduler = new AsyncScheduler
}
