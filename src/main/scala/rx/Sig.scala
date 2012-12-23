package rx

import util.continuations._

import java.util.concurrent.Semaphore

import annotation.tailrec
import concurrent.{Future, Promise}
import util.{DynamicVariable, Try}


/**
 * Contains a future that is kept up to date whenever the node's dependencies
 * change
 */
object Sig{
  def apply[T](calc: => T @cpsResult): Sig[T] = new Sig(() => calc)

  val stack = new ThreadLocal[List[Sig[_]]]{
    override def initialValue = Nil
  }
}

/**
 * A Sig is a signal that is defined relative to other signals, and updates
 * automatically when they change.
 *
 * @param calc The method of calculating the future of this Sig
 * @tparam T The type of the future this contains
 */
class Sig[+T](calc: () => T @cpsResult) extends Signal[T] with Reactor[Any]{

  @volatile private[this] var state = SigState[T](
    Promise[T],
    Nil,
    0,
    More(() => newCont, this, false).asInstanceOf[Cont[T]]
  )

  debug("initialized " + this.getClass.getName)
  /**
   * A random GUID set each time this Sigs continuation is evaluated.
   * If it changes over the course of evaluation, it means some other
   * thread has started concurrently evaluating, and the result of this
   * thread's execution should be discarded.
   */
  @volatile private[this] var execId = 0L
  /**
   * The currently stored continuation. If evaluation of this signal is blocked
   * on some dependency, or due to a level mismatch, this will be a Blocked that
   * can be executed to resume computation. If the evaluation is complete, this
   * will be a Done containing the final result
   */
  private[this] val lock = new Object()

  propagate(Map(this -> Set(this)))

  /**
   * A future containing the current value of this Signal
   */
  def future: Future[T] = state.currentValue.future

  private[this] def newCont = reset {
    Sig.stack.set(this :: Sig.stack.get())
    val x = Done(calc())
    Sig.stack.set(Sig.stack.get().tail)
    x
  }

  def getParents = state.parents

  def level = state.level

  /**
   * Continues running whatever continuation was stored, or starts running
   * a new continuation from scratch, depending on who pinged it. If there was
   * no interference, commits the changes at the end of the method. All the
   * interesting logic happens in the `update()` method, which returns a new
   * SigState that `ping` can then keep or discard.
   *
   * @return
   */
  def ping(rawIncoming: Seq[Emitter[_]]) = {
    debug("pinged " + rawIncoming.map(_.id) + " " + getParents.map(_.id))
    val pingId = util.Random.nextLong()
    this.execId = pingId

    val (returnValues, newState) = getUpdate(state, rawIncoming)

    if (pingId == execId) lock.synchronized{
      if (pingId == execId){

        state = newState
        newState.parents.foreach(_.linkChild(this))
        returnValues
      } else (Seq(), false)
    } else (Seq(), false)
  }

  /**
   * Logic involved with updating a Sig. This function takes a Sig together with
   * its initial SigState and some Emitters which are pinging it, and returns a new
   * SigState representing the state after the ping has occured. This function is
   * more or less pure, so the new SigState can either be swapped in or discarded
   * safely.
   */
  private[this] def getUpdate[T](initialState: SigState[T], rawIncoming: Seq[Emitter[_]]) = {
    val incoming = rawIncoming.filter(i => initialState.parents.contains(i) | i == this)

    if (incoming.isEmpty)  ((Seq(), false), initialState)
    else {
      val (nextCont, usedOldParents) = initialState.continuation match {
        case More(cont, blockedOn, _) if incoming == Seq(blockedOn) || incoming == Seq(this) =>
          Sig.stack.set(this :: Sig.stack.get())

          val x = (cont(), initialState.parents)
          Sig.stack.set(Sig.stack.get().tail)
          x
        case _ => (newCont.asInstanceOf[Cont[T]], Nil)
      }

      (nextCont, initialState.continuation) match {
        case (More(_, blockedOnA, true), More(_, blockedOnB, true)) if blockedOnA == blockedOnB =>
          (Seq(this), true) -> initialState
        case _ => {
          val (returnVal, newVal, newLevel, nextParents) = nextCont match {
            case More(_, s, _) => (
              Seq(this),
              initialState.currentValue,
              initialState.level max (s.level + 1),
              Some(s)
            )
            case Done(t: T) => (
              this.getChildren,
              Promise.successful(t),
              initialState.level,
              None
            )
          }

          (
            (returnVal, false),
            SigState(
              newVal,
              usedOldParents ++ nextParents.filter(!usedOldParents.contains(_)),
              newLevel,
              nextCont
            )
          )
        }
      }
    }
  }

  /**
   * Attempts to retrieve a value from the target Signal. Usually called inside
   * that Signal's apply() method. If that Signal is ready, and its propagation
   * level is smaller than this Sig's, it continues the evaluation immediately
   * with the Signal's value. If not, it halts the evaluation, returning a
   * Blocked containing a continuation that can be used to resume it.
   *
   * @param s is the Signal to extract a value from
   * @tparam A is the type of that Signal
   * @return
   */
  def pullSignal[A](s: Signal[A]): Try[A] @cpsResult = shift { resume: (Try[A] => Cont[Any]) =>
    s.future.value match {
      case Some(x) => More(() => resume(x), s, false)
      case _ => More(() => reset{resume(pullSignal(s))}, s, true)
    }
  }

}

/**
 * Immutable container to hold the mutable state of a Sig, effectively giving
 * us the ability to read/write all the fields atomically.
 */
case class SigState[T](
  currentValue: Promise[T],
  parents: Seq[Emitter[Any]],
  level: Long,
  continuation: Cont[T]
)