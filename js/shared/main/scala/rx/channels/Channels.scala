package rx.channels

import acyclic.file
import scala.concurrent.{Promise, Future}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

trait Source[+T]{
  def apply(): Future[T]
}

trait Sink[-T]{
  def update(t: T): Unit
}

trait Channel[T] extends Source[T] with Sink[T]

object Channel{
  /**
   * A channel that publishes any incoming events to all listeners,
   * dropping any events that come when nobody is listening.
   */
  class PubSub[T]() extends Channel[T]{
    var value: Promise[T] = null
    def apply() = {
      value = Option(value).getOrElse(Promise[T]())
      value.future
    }
    def update(t: T) = {
      value match{
        case null =>
        case v => v.success(t)
      }
      value = null
    }
  }

  /**
   * A channel that queues up events and feeds each event to a
   * single consumer. Events are buffered if there is no consumer
   * waiting for it, and consumers are buffered if there are no
   * events to consume.
   */
  class Queue[T]() extends Channel[T]{
    val queue = new AtomicReference[Either[Seq[Promise[T]],Seq[T]]](Right(Seq()))
    import queue._
    @tailrec final def update(t: T): Unit = get match {
      case r @ Right(ts) => // Queue doesn't have any waiters so enqueue this value
        if (!compareAndSet(r, Right(ts :+ t))) update(t)
      case l @ Left(Seq(p)) => // Queue has a single waiter
        if (!compareAndSet(l, Right(Seq()))) update(t)
        else p success t
      case l @ Left(p :: ps) => // Queue has multiple waiters
        if (!compareAndSet(l, Left(ps))) update(t)
        else p success t
    }

    @tailrec final def apply(): Future[T] = get match {
      case l @ Left(ps) => // Already has waiting consumers
        val p = Promise[T]()
        if (!compareAndSet(l, Left(ps :+ p))) apply()
        else p.future
      case r @ Right(Seq()) => // Empty queue, become first waiting consumer
        val p = Promise[T]()
        if (!compareAndSet(r, Left(Seq(p)))) apply()
        else p.future
      case r @ Right(t :: ts) => // Already has values. get the head
        if (!compareAndSet(r, Right(ts))) apply()
        else Future successful t
    }
  }
}
