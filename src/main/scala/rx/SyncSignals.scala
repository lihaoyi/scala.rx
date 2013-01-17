package rx

import rx.Flow.{Emitter, Signal}
import util.{DynamicVariable, Failure, Try}
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

    private[rx] val enclosingR = new DynamicVariable[DynamicSignal[Any]](null)
    private[rx] val enclosingT = new DynamicVariable[InTxn](null)


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

    private[this] val parents = Ref[Seq[Flow.Emitter[Any]]](Nil)

    private[this] val levelRef: Ref[Long] = Ref(0L)
    private[this] val timeStamp = Ref(System.nanoTime())
    private[this] val value: Ref[Try[T]] = Ref(fullCalc())

    def fullCalc() = {
      DynamicSignal.enclosingR.withValue(this){
        atomic{ txn =>
          DynamicSignal.enclosingT.withValue(txn){
            Try(calc())
          }
        }
      }

    }

    def getParents = parents.single()

    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      if (active && getParents.intersect(incoming).isDefinedAt(0)){

        val newValue = fullCalc()

        atomic{ implicit txn =>
          if (value() != newValue){
            value() = newValue
           getChildren
          }else Nil
        }
      }else {
        Nil
      }
    }

    def toTry = value.single()

    def level = levelRef.single()

    def addParent(e: Emitter[Any]) = atomic{ implicit txn =>
      parents() = parents() :+ e
    }

    def incrementLevel(l: Long) = atomic{ implicit txn =>
      levelRef() = math.max(l, levelRef())
    }
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

    def addParent(e: Emitter[Any]) {}

    def incrementLevel(l: Long) {}
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
