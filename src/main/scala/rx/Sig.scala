package rx

import util.{Success, Try, DynamicVariable}
import concurrent.{ExecutionContext, Future}


/**
 * Contains a future that is kept up to date whenever the node's dependencies
 * change
 */
object Sig{

  def apply[T](calc: => T)(implicit name: String = ""): Sig[T] = {

    new Sig(name, () => calc)
  }

  val enclosing = new DynamicVariable[SigState[Any]](null)
  val enclosingR = new DynamicVariable[Flow.Reactor[Any]](null)
}
case class SigState[+T](parents: Seq[Flow.Emitter[Any]],
                        value: Try[T],
                        level: Long)
/**
 * A Sig is a signal that is defined relative to other signals, and updates
 * automatically when they change.
 *
 * @param calc The method of calculating the future of this Sig
 * @tparam T The type of the future this contains
 */
class Sig[+T](val name: String, calc: () => T) extends Signal[T] with Flow.Reactor[Any]{

  @volatile var active = true
  @volatile private[this] var state: SigState[T] = fullCalc(Option(Sig.enclosing.value).map(_.level + 1).getOrElse(0))

  def fullCalc(lvl: Long = level): SigState[T] = {
    Sig.enclosing.withValue(SigState(Nil, null, lvl)){
      Sig.enclosingR.withValue(this){
        val newValue = Try(calc())
        Sig.enclosing.value.copy(value = newValue)
      }
    }
  }

  def getParents = state.parents

  def ping(incoming: Seq[Flow.Emitter[Any]]) = {
    if (active && getParents.intersect(incoming).isDefinedAt(0)){
      val newState = fullCalc()

      val enclosingLevel: Long = Option(Sig.enclosing.value).map(_.level + 1).getOrElse(0)
      val newLevel = math.max(newState.level, enclosingLevel)

      state = newState.copy(newState.parents, newState.value, newLevel)
      getChildren

    }else {
      Nil
    }
  }

  def toTry = state.value

  def level = state.level

  def currentValue = state.value.get
}
