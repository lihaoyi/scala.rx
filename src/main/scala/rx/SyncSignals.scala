package rx

import rx.Flow.Signal
import util.{DynamicVariable, Failure, Try}
import rx.SyncSignals.DynamicSignal.SigState

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
                            level: Long)
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
    @volatile private[this] var state: SigState[T] = fullCalc(Option(DynamicSignal.enclosing.value).map(_.level + 1).getOrElse(0))

    def fullCalc(lvl: Long = level): SigState[T] = {
      DynamicSignal.enclosing.withValue(SigState(Nil, null, lvl)){
        DynamicSignal.enclosingR.withValue(this){
          val newValue = Try(calc())
          DynamicSignal.enclosing.value.copy(value = newValue)
        }
      }
    }

    def getParents = state.parents

    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      if (active && getParents.intersect(incoming).isDefinedAt(0)){
        val newState = fullCalc()

        val enclosingLevel: Long = Option(DynamicSignal.enclosing.value).map(_.level + 1).getOrElse(0)
        val newLevel = math.max(newState.level, enclosingLevel)

        if(newState.value != state.value){
          state = newState.copy(level = newLevel)
          getChildren
        }else{
          Nil
        }
      }else {
        Nil
      }
    }

    def toTry = state.value

    def level = state.level

    def currentValue = state.value.get
  }

  def filterSig[T](source: Signal[T])(predicate: (Try[T], Try[T]) => Boolean) = {
    new WrapSignal(source)((x: Try[T], y: Try[T]) => if (predicate(x, y)) y else x)
  }

  /**
   * A Signal which wraps a source Signal and allows you to specify a transform
   * which will be applied whenever the source value changes. This decides on
   * a new value to take based on both the old and new values of the source.
   */
  class WrapSignal[T, A](source: Signal[A])(transformer: (Try[T], Try[A]) => Try[T])
    extends Signal[T]
    with Flow.Reactor[Any]{

    var lastResult = transformer(Failure(null), source.toTry)
    source.linkChild(this)

    def level = source.level + 1
    def name = "SkipFailure " + source.name
    def currentValue = lastResult.get
    def toTry = lastResult
    def getParents = Seq(source)
    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      val newTry = transformer(lastResult, source.toTry)
      if (newTry == toTry) Nil
      else {
        lastResult = newTry
        getChildren
      }
    }
  }

}
