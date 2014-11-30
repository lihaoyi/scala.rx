package rx
package ops
import acyclic.file
import scala.concurrent.{ExecutionContext, Future}

import scala.util.Try
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.lang.ref.WeakReference
import concurrent.duration._
import rx._
import rx.core._
import scala.Some
import scala.util.Success
import scala.Some
import rx.core.SpinSet
import rx.Rx
import scala.util.Success


/**
 * A [[Rx]] which flattens out an Rx[Future[T]] into a Rx[T]. If the first
 * Future has not yet arrived, the Async contains its default value.
 * Afterwards, it updates itself when and with whatever the Futures complete
 * with.
 *
 * The Async can be configured with a variety of Targets, to configure
 * its handling of Futures which complete out of order (RunAlways, DiscardLate)
 */
class Async[+T, P](default: => T,
                   source: Rx[Future[T]],
                   discardLate: Boolean)
                  (implicit ec: ExecutionContext, p: Propagator[P])
                   extends Rx[T]
                   with Incrementing[T]
                   with Reactor[Future[_]]{

  source.linkChild(this)
  def name = "Async " + source.name

  protected[this] type StateType = SpinState

  protected[this] val state = SpinSet(new SpinState(0, Try(default)))

  override def ping[P](incoming: Set[Emitter[_]])(implicit p: Propagator[P]) = {
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
    Set.empty
  }
  def parents = Set(source)

  def level = source.level + 1

  this.ping(parents)
}

/**
 * An [[Rx]] which wraps an existing [[Rx]] but only emits changes at most once
 * every `interval`.
 */
class Debounce[+T](source: Rx[T], interval: FiniteDuration)
                  (implicit scheduler: Scheduler, ex: ExecutionContext)
                   extends core.Dynamic[T](() => source(), "Debounced " + source.name){

  val nextPingTime = new AtomicReference(Deadline.now)

  override def ping[P: Propagator](incoming: Set[Emitter[_]]): Set[Reactor[_]] = {

    val npt = nextPingTime.get

    if (Deadline.now > npt && nextPingTime.compareAndSet(npt, Deadline.now + interval)) {
      super.ping(incoming)
    } else {
      scheduler.scheduleOnce(npt - Deadline.now){
        if (nextPingTime.compareAndSet(npt, Deadline.now)) {
          if(ping(incoming) != Nil)this.propagate()
        }
      }
      Set.empty
    }
  }
  override def level = source.level + 1
}

/**
 * An [[Rx]] which wraps and existing [[Rx]] but delays the propagation by
 * `delay`.
 */
class Delay[+T](source: Rx[T], delay: FiniteDuration)
               (implicit scheduler: Scheduler, ex: ExecutionContext)
                extends core.Dynamic[T](() => source(), "Delayed " + source.name){

  override def ping[P: Propagator](incoming: Set[Emitter[_]]): Set[Reactor[_]] = {
    scheduler.scheduleOnce(delay){
      if(super.ping(incoming) != Nil) this.propagate()
    }
    Set.empty
  }

  override def level = source.level + 1
}


object Timer{
  def apply[P](interval: FiniteDuration, delay: FiniteDuration = 0 seconds)
              (implicit scheduler: Scheduler, p: Propagator[P], ec: ExecutionContext) = {

    new Timer(interval, delay)
  }
}

/**
 * An [[Rx]] which begins a propagation once every `interval` after an initial
 * delay of `delay`. Its value is the number of times it has emitted.
 */
class Timer[P](interval: FiniteDuration, delay: FiniteDuration)
              (implicit scheduler: Scheduler, p: Propagator[P], ec: ExecutionContext)
               extends Rx[Long]{


  private[rx] val count = new AtomicLong(0L)
  private[this] val holder = new WeakTimerHolder(new WeakReference(this), interval, delay)

  def name = "Timer" + this.hashCode()

  def level = 0
  def toTry = Success(count.get)
  def parents: Set[Emitter[_]] = Set.empty
  def ping[P: Propagator](incoming: Set[Emitter[_]]) = {
    this.children
  }
}

/**
 * Wraps a timer, which is managed by Akka or the DOM depending if this is
 * JVM or Javascript. Ensures that the timer gets shut off when the [[Timer]]
 * is killed or garbage collected.
 */
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
        case timer if timer.alive =>
          schedule(interval)
          timer.count.getAndIncrement
          timer.propagate()

        case _ =>
      }
    }
  }

  schedule(delay)
}
