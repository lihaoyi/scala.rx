package rx

import rx.Flow.{Reactor, Emitter, Signal}
import util.{DynamicVariable, Failure, Try}
import scala.util.Success
import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec
import akka.agent.Agent
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
    def apply[T](calc: => T)(implicit p: Propagator): DynamicSignal[T] = {
      new DynamicSignal(() => calc)
    }

    def apply[T](x: =>Nothing = ???, name: String = "", default: T = null.asInstanceOf[T])
                (calc: => T)
                (implicit p: Propagator): DynamicSignal[T] = {
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
  class DynamicSignal[+T](calc: () => T, val name: String = "", default: T = null.asInstanceOf[T])
                         (implicit p: Propagator)
                          extends Flow.Signal[T] with Flow.Reactor[Any]{
    import p.executionContext
    @volatile var active = true
    private[this] class State(val parents: Seq[Flow.Emitter[Any]],
                              val level: Long,
                              invalue: =>Try[T]){
      lazy val value = invalue
    }

    private[this] val state = Agent(new State(Nil, 0, Success(default)))
    var init = false
    def fullCalc() = {
      DynamicSignal.enclosing.withValue(Some(this -> Nil)){
        (Try(calc()), DynamicSignal.enclosing.value.get._2)
      }
    }

    private[this] def getState(minLevel: Long) = {
      init = true
      val (newValue, deps) = fullCalc()

      new State(
        deps,
        (minLevel :: deps.map(_.level)).max,
        newValue
      )
    }

    def getParents = state().parents

    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      if (active && getParents.intersect(incoming).isDefinedAt(0)){
        val oldValue = state().value
        state alter {x =>
          getState(this.level)
        } map {x =>
          if (x.value != oldValue)
            getChildren
          else
            Nil
        }

      }else Future.successful(Nil)
    }

    def initialize = {
      Await.result(
        state.alter {x =>  getState(this.level) }
          .map(xml => this.propagate())
          .map{x => println("altereed"); state().value},
        10 seconds
      )
    }
    def toTry = {
      if (init == false){
        println("altering")

        initialize
      }

      state().value

    }

    def level = state().level

    override def linkChild[R >: T](child: Reactor[R]): Unit = {
      if (init == false) initialize
      super.linkChild(child)
    }
  }

  abstract class WrapSignal[T, A](source: Signal[T], prefix: String)
                                 (implicit p: Propagator)
                                  extends Signal[A] with Flow.Reactor[Any]{
    source.linkChild(this)
    def level = source.level + 1
    def getParents = Seq(source)
    def name = prefix + " " + source.name
  }

  class FilterSignal[T](source: Signal[T])
                       (transformer: (Try[T], Try[T]) => Try[T])
                       (implicit p: Propagator)
                        extends WrapSignal[T, T](source, "FilterSignal"){
    import p.executionContext

    private[this] val state = Agent(transformer(Failure(null), source.toTry))

    def toTry = state()

    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      val newTime = System.nanoTime()
      val newValue = transformer(state(), source.toTry)

      if (state() == newValue) Future.successful(Nil)
      else {
        state alter newValue map (x => getChildren)
      }
    }
  }

  class MapSignal[T, A](source: Signal[T])
                       (transformer: Try[T] => Try[A])
                       (implicit p: Propagator)
                        extends WrapSignal[T, A](source, "MapSignal"){

    import p.executionContext

    private[this] val state = Agent(transformer(source.toTry))

    def toTry = state()
    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      val newTime = System.nanoTime()
      val newValue = transformer(source.toTry)
      state alter newValue map  (x => getChildren)
    }
  }


}
