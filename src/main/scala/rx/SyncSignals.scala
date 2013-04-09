package rx

import rx.Flow.{Reactor, Emitter, Signal}
import util.{DynamicVariable, Failure, Try}
import scala.util.Success
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import annotation.tailrec

import concurrent.{Await, Future}
import concurrent.duration._
import reflect.ClassTag

/**
 * A collection of Signals that update immediately when pinged. These should
 * generally not be created directly; instead use the alias Rx in the package
 * to construct DynamicSignals, and the extension methods defined in Combinators
 * to build SyncSignals from other Rxs.
 */
object SyncSignals {

  object DynamicSignal{
    def apply[T, P: Propagator](calc: => T, name: String = "", default: T = null.asInstanceOf[T]) = {
      new DynamicSignal(() => calc, name, default)
    }

    private[rx] val enclosing = new DynamicVariable[Option[(DynamicSignal[Any], List[Signal[Any]])]](None)
  }

  /**
   * A DynamicSignal is a signal that is defined relative to other signals, and
   * updates automatically when they change.
   *
   * Note that while the propagation tries to minimize the number of times a
   * DynamicSignal needs to be recalculated, there is always going to be some
   * redundant recalculation. Since this is unpredictable, the body of a
   * DynamicSignal should always be side-effect free
   *
   * @param calc The method of calculating the future of this DynamicSignal
   * @tparam T The type of the future this contains
   */
  class DynamicSignal[+T](calc: () => T,
                          val name: String = "",
                          default: T = null.asInstanceOf[T])
                          extends Flow.Signal[T]
                          with Flow.Reactor[Any]
                          with SpinlockSignal[T]{

    @volatile var active = true

    protected[this] class State(val parents: Seq[Flow.Emitter[Any]],
                                val level: Long,
                                timestamp: Long,
                                value: Try[T])
                                extends SpinState(timestamp, value)

    object Initial extends State(Nil, 0, 0, Success(default))
    type StateType = State

    protected[this] val state = Atomic(makeState)

    def makeState = {
      val startCalc = getStamp
      val (newValue, deps) =
        DynamicSignal.enclosing.withValue(Some(this -> Nil)){
          (Try(calc()), DynamicSignal.enclosing.value.get._2)
        }

      new State(
        deps,
        (0L :: deps.map(_.level)).max,
        startCalc,
        newValue
      )
    }

    def getParents = state().parents

    override def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]): Seq[Reactor[Nothing]] = {
      if (active && getParents.intersect(incoming).isDefinedAt(0)){
        super.ping(incoming)
      } else Nil
    }

    def level = state().level
  }

  /**
   * Signals whose state contains an auto-incrementing "timestamp" in order to
   * reject out of order completions
   */
  trait IncrSignal[+T] extends Flow.Signal[T]{
    private val updateCount = new AtomicLong(0)
    def getStamp = updateCount.getAndIncrement

    class SpinState(val timestamp: Long,
                    val value: Try[T])

    type StateType <: SpinState
    protected[this] val state: Atomic[StateType]
    def toTry = state().value

  }
  trait SpinlockSignal[+T] extends IncrSignal[T]{

    def makeState: StateType

    def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]): Seq[Reactor[Nothing]] = {
      val newState = makeState
      val set = state.spinSetOpt{oldState =>
        if (newState.value != oldState.value
          && newState.timestamp >= oldState.timestamp){
          Some(newState)
        }else{
          None
        }
      }
      if(set) this.getChildren
      else Nil
    }
  }

  abstract class WrapSignal[T, +A](source: Signal[T], prefix: String)
                                  extends Signal[A]
                                  with Flow.Reactor[Any]{
    source.linkChild(this)
    def level = source.level + 1
    def getParents = Seq(source)
    def name = prefix + " " + source.name
  }


  class ReduceSignal[T](source: Signal[T])
                       (transformer: (Try[T], Try[T]) => Try[T])
                        extends WrapSignal[T, T](source, "ReduceSignal")
                        with SpinlockSignal[T]{

    type StateType = SpinState
    protected[this] val state = Atomic(new SpinState(
      getStamp,
      transformer(Failure(null), source.toTry)
    ))

    def makeState = new SpinState(
      getStamp,
      transformer(state().value, source.toTry)
    )
  }

  /**
   * A Signal[A] which is a direct transformation of another Signal[T] via a
   * transformation function. Generally created via the `.map()` method on a
   * Signal[A].
   */
  class MapSignal[T, +A](source: Signal[T])
                       (transformer: Try[T] => Try[A])
                        extends WrapSignal[T, A](source, "MapSignal")
                        with SpinlockSignal[A]{

    type StateType = SpinState
    def makeState = new SpinState(
      getStamp,
      transformer(source.toTry)
    )

    protected[this] val state = Atomic(makeState)
  }


}
