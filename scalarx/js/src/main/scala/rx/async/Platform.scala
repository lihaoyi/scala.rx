package rx.async

object Platform  {
  implicit lazy val DefaultScheduler: Scheduler = new AsyncScheduler
}
