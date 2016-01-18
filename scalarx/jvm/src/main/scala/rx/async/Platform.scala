package rx.async

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

object Platform {
  implicit lazy val DefaultScheduler: Scheduler = new AsyncScheduler(
    Executors.newSingleThreadScheduledExecutor(),
    ExecutionContext.Implicits.global
  )
}