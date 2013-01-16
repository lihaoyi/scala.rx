package rx

import rx.Flow.Signal
import util.{DynamicVariable, Failure, Try}
import rx.SyncSignals.DynamicSignal.SigState

object SyncSignals {
  /**
   * Contains a future that is kept up to date whenever the node's dependencies
   * change
   */
  object DynamicSignal{

    def apply[T](calc: => T)(implicit name: String = ""): DynamicSignal[T] = {
      new DynamicSignal(name, () => calc)
    }

    val enclosing = new DynamicVariable[SigState[Any]](null)
    val enclosingR = new DynamicVariable[Flow.Reactor[Any]](null)
    case class SigState[+T](parents: Seq[Flow.Emitter[Any]],
                            value: Try[T],
                            level: Long)
  }

  /**
   * A DynamicSignal is a signal that is defined relative to other signals, and updates
   * automatically when they change.
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
    new WrapSig(source)((x: Try[T], y: Try[T]) => if (predicate(x, y)) y else x)
  }

  class WrapSig[T, A](source: Signal[A])(transformer: (Try[T], Try[A]) => Try[T])
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
