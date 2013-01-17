package rx

import rx.Flow.Signal
import util.{DynamicVariable, Failure, Try}
import rx.SyncSignals.DynamicSignal.SigState
import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec
import scala.concurrent.stm._

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
    def apply[T](calc: => T)(implicit name: String = ""): DynamicSignal[T] = {
      new DynamicSignal(name, () => calc)
    }

    private[rx] val enclosing = new DynamicVariable[SigState[Any]](null)
    private[rx] val enclosingR = new DynamicVariable[Flow.Reactor[Any]](null)

    case class SigState[+T](parents: Seq[Flow.Emitter[Any]],
                            value: Try[T],
                            level: Long,
                            timeStamp: Long = System.nanoTime())
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
  class DynamicSignal[+T](val name: String, calc: () => T) extends Flow.Signal[T] with Flow.Reactor[Any]{

    @volatile var active = true
    private[this] val state = Ref(fullCalc(Option(DynamicSignal.enclosing.value).map(_.level + 1).getOrElse(0)))

    def fullCalc(lvl: Long = level): SigState[T] = {
      DynamicSignal.enclosing.withValue(SigState(Nil, null, lvl)){
        DynamicSignal.enclosingR.withValue(this){
          val newValue = Try(calc())
          DynamicSignal.enclosing.value.copy(value = newValue)
        }
      }
    }

    def getParents = state.single().parents

    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      if (active && getParents.intersect(incoming).isDefinedAt(0)){

        lazy val newState: SigState[T] = fullCalc()


        atomic{ implicit txn =>
          state() = newState
          if (state().timeStamp < newState.timeStamp) getChildren
          else Nil
        }
      }else {
        Nil
      }
    }

    def toTry = state.single().value

    def level = state.single().level
  }


  class FilterSignal[T](source: Signal[T])(transformer: (Try[T], Try[T]) => Try[T])
    extends Signal[T]
    with Flow.Reactor[Any]{

    private[this] val lastResult = Ref(transformer(Failure(null), source.toTry))
    println(source.getChildren)
    source.linkChild(this)
    def level = source.level + 1
    def name = "FilterSignal " + source.name
    def toTry = lastResult.single()
    def getParents = Seq(source)
    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      println("FilterSignal")
      val newTime = System.nanoTime()
      val newValue = transformer(lastResult.single(), source.toTry)

      atomic{ implicit txn =>
        if (lastResult() == newValue) Nil
        else {
          lastResult() = newValue
          getChildren
        }
      }
    }
  }
  class MapSignal[T, A](source: Signal[T])(transformer: Try[T] => Try[A])
    extends Signal[A]
    with Flow.Reactor[Any]{

    private[this] val lastValue = Ref(transformer(source.toTry))
    private[this] val lastTime = Ref(System.nanoTime())
    source.linkChild(this)

    def level = source.level + 1
    def name = "MapSignal " + source.name
    def toTry = lastValue.single()
    def getParents = Seq(source)
    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      val newTime = System.nanoTime()
      val newValue = transformer(source.toTry)
      atomic{ implicit txn =>
        if (newTime > lastTime()){
          lastValue() = newValue
          lastTime() = newTime
          getChildren
        }else Nil
      }
    }
  }


}
