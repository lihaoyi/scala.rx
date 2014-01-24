package rx
package ops

import scala.concurrent.{ExecutionContext, Future}

import scala.util.Try
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.lang.ref.WeakReference
import concurrent.duration._
import rx._
import rx.core.Atomic
import scala.Some
import scala.util.Success
import rx.core.{Reactor, Emitter, Propagator, Rx}


/**
 * A Rx which flattens out an Rx[Future[T]] into a Rx[T]. If the first
 * Future has not yet arrived, the Async contains its default value.
 * Afterwards, it updates itself when and with whatever the Futures complete
 * with.
 *
 * The Async can be configured with a variety of Targets, to configure
 * its handling of Futures which complete out of order (RunAlways, DiscardLate)
 */
private[rx] class Async[+T, P](default: => T,
                               source: Rx[Future[T]],
                               discardLate: Boolean)
                              (implicit ec: ExecutionContext, p: Propagator[P])
                               extends Rx[T]
                               with Incrementing[T]
                               with Reactor[Future[_]]{

  source.linkChild(this)
  def name = "Async " + source.name

  type StateType = SpinState

  protected[this] val state = Atomic(new SpinState(0, Try(default)))

  override def ping[P](incoming: Seq[Emitter[Any]])(implicit p: Propagator[P]): Seq[Reactor[Nothing]] = {
    val stamp = getStamp
    source().onComplete{ x =>
      val set = state.spinSetOpt{oldState =>
        if (x != state().value && (stamp >= oldState.timestamp || !discardLate)){
          Some(new SpinState(stamp, x))
        }else{
          None
        }
      }

      if(set) propagate()
    }
    Nil
  }
  def parents = Seq(source)

  protected[rx] def level = source.level + 1

  this.ping(Seq(source))
}


private[rx] class Debounce[+T](source: Rx[T], interval: FiniteDuration)
                          (implicit scheduler: Scheduler, ex: ExecutionContext)
                           extends core.Dynamic[T](() => source(), "Debounced " + source.name){

  val nextPingTime = new AtomicReference(Deadline.now)

  override def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]] = {

    val npt = nextPingTime.get

    if (Deadline.now > npt && nextPingTime.compareAndSet(npt, Deadline.now + interval)) {
      super.ping(incoming)
    } else {
      scheduler.scheduleOnce(npt - Deadline.now){
        if (nextPingTime.compareAndSet(npt, Deadline.now)) {
          if(ping(incoming) != Nil)this.propagate()
        }
      }
      Nil
    }
  }
  override def level = source.level + 1
}

private[rx] class Delay[+T](source: Rx[T], delay: FiniteDuration)
               (implicit scheduler: Scheduler, ex: ExecutionContext)
  extends core.Dynamic[T](() => source(),"Delayed " + source.name){

  override def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]] = {
    scheduler.scheduleOnce(delay){
      if(super.ping(incoming) != Nil) this.propagate()
    }
    Nil
  }

  protected[rx] override def level = source.level + 1
}


private[rx] object Timer{
  def apply[P](interval: FiniteDuration, delay: FiniteDuration = 0 seconds)
              (implicit scheduler: Scheduler, p: Propagator[P], ec: ExecutionContext) = {

    new Timer(interval, delay)
  }
}

private[rx] class Timer[P](interval: FiniteDuration, delay: FiniteDuration)
                          (implicit scheduler: Scheduler, p: Propagator[P], ec: ExecutionContext)
                           extends Rx[Long]{
  val count = new AtomicLong(0L)
  val holder = new WeakTimerHolder(new WeakReference(this), interval, delay)

  def name = "Timer"

  def timerPing() = {
    count.getAndIncrement
    propagate()
  }
  protected[rx] def level = 0
  def toTry = Success(count.get)
  def parents: Seq[Emitter[Any]] = Nil
  def ping[P: Propagator](incoming: Seq[Emitter[Any]]) = this.children
}

private[rx] class WeakTimerHolder[P](val target: WeakReference[Timer[P]],
                                     interval: FiniteDuration,
                                     delay: FiniteDuration)
                                    (implicit scheduler: Scheduler,
                                     p: Propagator[P],
                                     ec: ExecutionContext){

  def schedule(delay: FiniteDuration): Unit = {
    scheduler.scheduleOnce(delay){
      (target.get: Timer[_]) match{
        case null =>
        case timer =>
          schedule(interval)
          timer.timerPing()
      }
    }
  }
  schedule(delay)
}
class AsyncRx[T](source: Rx[Future[T]]){
  /**
   * Flattens out an Rx[Future[T]] into a Rx[T]. If the first
   * Future has not yet arrived, the Async contains its default value.
   * Afterwards, it updates itself when and with whatever the Futures complete
   * with.
   *
   * @param default The initial value of this [[Rx]] before any `Future` has completed.
   * @param discardLate Whether or not to discard the result of `Future`s which complete "late":
   *                    meaning it was created earlier but completed later than some other `Future`.
   */
  def async[P](default: T,
               discardLate: Boolean = true)
              (implicit executor: ExecutionContext, p: Propagator[P]): Rx[T] = {
    new Async(default, source, discardLate)

  }
}