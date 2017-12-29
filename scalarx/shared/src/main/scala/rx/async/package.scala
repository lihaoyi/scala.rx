package rx

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Deadline, FiniteDuration}

package object async {
  import acyclic.pkg
  implicit class FutureCombinators[T](val f: Future[T]) extends AnyVal {
    def toRx(initial: T)(implicit ec: ExecutionContext, ctx: Ctx.Owner): Rx[T] = {
      @volatile var completed: T = initial
      val ret = Rx.build { (owner, data)  => completed }(ctx)
      f.map { v => completed = v ; ret.recalc() }
      ret
    }
  }

  implicit class AsyncCombinators[T](val n: rx.Rx[T]) extends AnyVal {
    def debounce(interval: FiniteDuration)(implicit scheduler: Scheduler, ctx: Ctx.Owner): Rx[T] = {
      @volatile var npt = Deadline.now
      @volatile var task = Option.empty[Cancelable]
      lazy val ret: Rx.Dynamic[T] = Rx.build { (owner, data) =>
        n.Internal.addDownstream(data)
        if(Deadline.now >= npt) {
          npt = Deadline.now + interval
          n.now
        } else {
          task.foreach(_.cancel())
          task = Some(scheduler.scheduleOnce(npt - Deadline.now) {
            ret.propagate()
          })
          ret()(data)
        }
      }(ctx)
      ret
    }

    def delay(amount: FiniteDuration)(implicit scheduler: Scheduler, ctx: Ctx.Owner): Rx[T] = {
      @volatile var fired = Deadline.now - amount
      @volatile var waiting = 0
      val next: Var[T] = Var(n.now)
      n.foreach { i =>
        if(Deadline.now >= fired + amount) {
          fired = Deadline.now
          next() = i
        } else {
          waiting += 1
          scheduler.scheduleOnce(fired + (amount*waiting) - Deadline.now) {
            waiting -= 1
            require(waiting >= 0)
            next() = i
          }
        }
      }
      Rx.build { (owner, data)  => next.Internal.addDownstream(data); next.now }(ctx)
    }
  }

  object Timer {
    import scala.concurrent.duration._
    def apply(interval: FiniteDuration)(implicit scheduler: Scheduler, ctx: Ctx.Owner): Rx[Long] = {
      @volatile var tick = 0l
      lazy val ret: Rx.Dynamic[Long] = Rx.build { (owner, data)  =>
        scheduler.scheduleOnce(interval) {
          tick += 1
          ret.recalc()
        }
        tick
      }(ctx)
      ret
    }
  }
}
