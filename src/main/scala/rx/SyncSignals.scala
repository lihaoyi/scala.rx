package rx

import rx.Flow.{Reactor, Emitter, Signal}
import util.{DynamicVariable, Failure, Try}
import scala.util.Success
import java.util.concurrent.atomic.AtomicReference
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
    /**
     * Provides a nice wrapper to use to create DynamicSignals
     */
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
                          default: T = null.asInstanceOf[T],
                          changeFilter: Boolean = true)
                          extends Flow.Signal[T] with Flow.Reactor[Any]{

    @volatile var active = true
    private[this] class State(val parents: Seq[Flow.Emitter[Any]],
                              val level: Long,
                              val timestamp: Long,
                              val value: Try[T])


    private[this] val state = Atomic(getState(0))



    private[this] def getState(minLevel: Long) = {

      val startCalc = System.currentTimeMillis()
      val (newValue, deps) =
        DynamicSignal.enclosing.withValue(Some(this -> Nil)){
          (Try(calc()), DynamicSignal.enclosing.value.get._2)
        }

      new State(
        deps,
        (minLevel :: deps.map(_.level)).max,
        startCalc,
        newValue
      )
    }

    def getParents = state().parents

    def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]): Seq[Reactor[Nothing]] = {

      if (active && getParents.intersect(incoming).isDefinedAt(0)){
        val newState = getState(this.level)
        val set = state.spinSetOpt{oldState =>
          if ((!changeFilter || newState.value != oldState.value)
              && newState.timestamp > oldState.timestamp){
            Some(newState)
          }else{
            None
          }
        }
        if(set) getChildren
        else Nil
      } else Nil
    }

    def toTry = state().value

    def level = state().level

  }

  abstract class WrapSignal[T, A](source: Signal[T], prefix: String)
                                  extends Signal[A] with Flow.Reactor[Any]{
    source.linkChild(this)
    def level = source.level + 1
    def getParents = Seq(source)
    def name = prefix + " " + source.name
  }

  class FilterSignal[T](source: Signal[T])
                       (transformer: (Try[T], Try[T]) => Try[T])
                        extends WrapSignal[T, T](source, "FilterSignal"){

    private[this] val state = Atomic(transformer(Failure(null), source.toTry))

    def toTry = state()

    def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]) = {
      val set = state.spinSetOpt{ v =>
        val newValue = transformer(state(), source.toTry)
        if (v == newValue) None
        else Some(newValue)
      }
      if (set) getChildren else Nil
    }
  }

  class MapSignal[T, A](source: Signal[T])
                       (transformer: Try[T] => Try[A])
                        extends WrapSignal[T, A](source, "MapSignal"){


    private[this] val state = Atomic(transformer(source.toTry))

    def toTry = state()
    def ping[P: Propagator](incoming: Seq[Flow.Emitter[Any]]) = {
      state() = transformer(source.toTry)
      getChildren
    }
  }


}
