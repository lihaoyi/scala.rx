package rx

import concurrent.{ExecutionContext, Future}
import java.util.concurrent.atomic.{AtomicReference, AtomicLong, AtomicInteger}
import util.{Success, Try}
import concurrent.duration._
import akka.actor.{Actor, Cancellable, ActorSystem}
import rx.Flow.{Emitter, Reactor, Signal}
import rx.SyncSignals.{IncrSignal, SpinlockSignal, DynamicSignal}
import java.lang.ref.WeakReference


/**
 * A collection of Rxs which may spontaneously update itself asynchronously,
 * even when nothing is going on. Use the extension methods in Combinators to
 * create these from other Rxs.
 *
 * These Rxs all required implicit ExecutionContexts and ActorSystems, in order
 * to properly schedule and fire the asynchronous operations.
 */
object AsyncSignals{

  /**
   * A Rx which flattens out an Rx[Future[T]] into a Rx[T]. If the first
   * Future has not yet arrived, the AsyncSig contains its default value.
   * Afterwards, it updates itself when and with whatever the Futures complete
   * with.
   *
   * The AsyncSig can be configured with a variety of Targets, to configure
   * its handling of Futures which complete out of order (RunAlways, DiscardLate)
   */
  class AsyncSig[+T, P](default: => T,
                     source: Signal[Future[T]],
                     discardLate: Boolean)
                    (implicit ec: ExecutionContext, p: Propagator[P])
                     extends Signal[T] with IncrSignal[T] with Flow.Reactor[Future[_]]{

    source.linkChild(this)
    def name = "Async " + source.name

    type StateType = SpinState

    protected[this] val state = Atomic(new SpinState(0, Try(default)))

    override def ping[P](incoming: Seq[Flow.Emitter[Any]])(implicit p: Propagator[P]): Seq[Reactor[Nothing]] = {
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
    def getParents = Seq(source)

    def level = source.level + 1

    this.ping(Seq(source))
  }


  class DebouncedSignal[+T](source: Signal[T], interval: FiniteDuration)
                           (implicit system: ActorSystem, ex: ExecutionContext)
                            extends DynamicSignal[T](() => source(), "Debounced " + source.name, source()){

    val nextPingTime = new AtomicReference(Deadline.now)

    override def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]): Seq[Reactor[Nothing]] = {

      val npt = nextPingTime.get

      if (Deadline.now > npt && nextPingTime.compareAndSet(npt, Deadline.now + interval)) {
        super.ping(incoming)
      } else {
        system.scheduler.scheduleOnce(npt - Deadline.now){
          if (nextPingTime.compareAndSet(npt, Deadline.now)) {
            if(ping(incoming) != Nil)this.propagate()
          }
        }
        Nil
      }
    }
    override def level = source.level + 1
  }

  class DelaySignal[+T](source: Signal[T], delay: FiniteDuration)
                       (implicit system: ActorSystem, ex: ExecutionContext)
                        extends DynamicSignal[T](() => source(), "Delayed " + source.name, source()){

    override def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]): Seq[Reactor[Nothing]] = {
      system.scheduler.scheduleOnce(delay){
        if(super.ping(incoming) != Nil) this.propagate()
      }
      Nil
    }

    override def level = source.level + 1
  }


  object Timer{
    def apply[P](interval: FiniteDuration, delay: FiniteDuration = 0 seconds)
                (implicit system: ActorSystem, p: Propagator[P], ec: ExecutionContext) = {

      new Timer(interval, delay)
    }
  }

  class Timer[P](interval: FiniteDuration, delay: FiniteDuration)
                (implicit system: ActorSystem, p: Propagator[P], ec: ExecutionContext)
                 extends Signal[Long]{
    val count = new AtomicLong(0L)
    val holder = new WeakTimerHolder(new WeakReference(this), interval, delay)

    def name = "Timer"

    def timerPing() = {
      count.getAndIncrement
      propagate()
    }
    def level = 0
    def toTry = Success(count.get)
  }

  class WeakTimerHolder[P](val target: WeakReference[Timer[P]], interval: FiniteDuration, delay: FiniteDuration)
                          (implicit system: ActorSystem, p: Propagator[P], ec: ExecutionContext){

    val scheduledTask: Cancellable = system.scheduler.schedule(delay, interval){
      target.get() match{
        case null => scheduledTask.cancel()
        case timer => timer.timerPing()
      }
    }
  }
}
