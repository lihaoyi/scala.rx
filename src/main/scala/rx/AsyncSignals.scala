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
                        extends Signal[T] with IncrSignal[T]{

    def name = "Async " + source.name

    type StateType = SpinState

    protected[this] val state = Atomic(new SpinState(0, Try(default)))

    private[this] val listener = Obs(source){
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
    }

    listener.trigger()

    def level = source.level + 1
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
          // run the ping only if nobody else has successfully run a ping
          // since i was scheduled. If someone else ran a ping, do nothing
          if (nextPingTime.compareAndSet(npt, Deadline.now)) {
            if(ping(incoming) != Nil){
              this.propagate()
            }
          }
        }
        Nil
      }
    }


    override def level = source.level + 1
  }
/*
  /**
   * A Rx which does not change more than once per `interval` units of time. This
   * can cause it to change asynchronously, as an update which is ignored (due to
   * coming in before the interval has passed) will get spontaneously.
   */
  class ImmediateDebouncedSignal[+T](source: Signal[T], interval: FiniteDuration)
                                    (implicit system: ActorSystem, ex: ExecutionContext, p: Propagator)
                                    extends DynamicSignal[T]("debounced " + source.name, () => source()){
    private[this] case class State(nextTime: Deadline, lastOutput: Option[Cancellable])
    val state = Agent(State(Deadline.now, None))

    override def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      if (active && getParents.intersect(incoming).isDefinedAt(0)){

        val (pingOut, schedule) = {
          val timeLeft = state().nextTime - Deadline.now

          (timeLeft.toMillis, state().lastOutput) match{
            case (t, _) if t < 0 =>
              nextTime() = Deadline.now + interval
              super.ping(incoming) -> None
            case (t, None) =>
              lastOutput() = Some(null)
              Nil -> Some(timeLeft)
            case (t, Some(_)) =>
              Nil -> None
          }
        }
        schedule match{
          case Some(timeLeft) =>
            lastOutput.single() = Some(system.scheduler.scheduleOnce(timeLeft){
              super.ping(incoming)
              this.propagate()
            })
          case _ => ()
        }
        pingOut
      } else Nil

    }
  }

  class DelayedRebounceSignal[+T](source: Signal[T], interval: FiniteDuration, delay: FiniteDuration)
                                  (implicit system: ActorSystem, ex: ExecutionContext, p: Propagator)
  extends Settable(source.now){
    def name = "delayedDebounce " + source.name

    private[this] val counter = new AtomicLong(0)
    private[this] val nextTime = Ref(Deadline.now)
    private[this] val lastOutput: Ref[Option[Long]] = Ref(None)

    private[this] val listener = Obs(source){
      val id = counter.getAndIncrement
      atomic{ implicit txn =>
        (lastOutput(), nextTime() - Deadline.now)  match {
          case (Some(_), _) => None
          case (None, timeLeft) =>
            lastOutput() = Some(id)
            Some(if (timeLeft < 0.seconds) delay else timeLeft)
        }
      } match {
        case None => ()
        case Some(next) =>
        system.scheduler.scheduleOnce(next){
          atomic{ implicit txn =>
            lastOutput() = None
            nextTime() = Deadline.now + interval
          }
          this.updateS(source.now)
        }
      }
    }
  }*/

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
