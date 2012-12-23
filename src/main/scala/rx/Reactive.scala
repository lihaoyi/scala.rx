package rx

import concurrent.{Await, Future}

import scala.util.continuations._

import ref.WeakReference
import collection.mutable

import concurrent.duration.Duration

import util.Try

/**
 * Represents the states a continuation can be in.
 *
 * @tparam T the result-type of this continuation
 */
sealed trait Cont[+T]

/**
 * A continuation which is encountered a Signal to pull a value fro     m, but is in
 * a position to be continued immediately.
 *
 * @param next can be called to continue running the computation
 * @param signal the Signal that was encountered
 * @tparam T the result-type of this continuation
 */
case class More[+T](next: () => Cont[T], signal: Signal[_], blocked: Boolean) extends Cont[T]

/**
 * A continuation which has completed its execution and is ready with
 * a result
 *
 * @param result is the result of the execution
 * @tparam T the result-type of this continuation
 */
case class Done[+T](result: T) extends Cont[T]

/**
 * A Signal is a Val that can change over time, emitting pings whenever it
 * changes. It can be thought of as an extension of Futures: a Future
 * can only be completed once, and emit one event. A Signal, while also
 * possibly starting-off un-set, can be set as many times as you want,
 * generating an arbitrary number of events.
 *
 * @tparam T The type of the future this signal contains
 */
trait Signal[+T] extends Emitter[T]{

  def future: Future[T]

  def isCompleted: Boolean = future.isCompleted

  def getTry[A](): Try[T] @cpsResult =  {
    Sig.stack.get().head.pullSignal(this)
  }

  def apply[A](): T @cpsResult =  {
    getTry().get
  }

  def now(dur: Duration = Duration.Zero) = Await.result(future, dur)
}

/**
 * Something that emits events. Manages a list of WeakReferences containing
 * listeners which need to be pinged when an event is fired.
 *
 * @tparam T The type of the events emitted
 */
trait Emitter[+T] extends Id with Leveled{

  private[this] val children: mutable.WeakHashMap[Reactor[T], Unit] = new mutable.WeakHashMap()

  /**
   * @return a list of children to ping
   */
  def getChildren: Seq[Reactor[Nothing]] = this.synchronized {
    children.keys.toSeq
  }

  /**
   * Clears the list of children
   */
  def dropChildren() = this.synchronized {
    children.clear()
  }

  /**
   * Adds a single child to the list of children
   * @param child is the child to be added
   * @tparam R the type of that child
   */
  def linkChild[R >: T](child: Reactor[R]) = this.synchronized {
    children(child) = ()
  }
}

/**
 * Something that can receive events
 *
 * @tparam T The type of the event received
 */
trait Reactor[-T] extends Id {

  def level: Long

  /**
   * @return the list of parents
   */
  def getParents: Seq[Emitter[Any]]


  /**
   * Causes this reactor to update. Does not directly update its children
   * (if any), but rather returns a list of children to the caller so the
   * caller can ping them directly.
   *
   * @param e a list of Emitters that pinged this Reactor
   * @return A list of children that need to be pinged as a result of this
   *         reactor being pinged.
   *
   */
  def ping(e: Seq[Emitter[_]]): (Seq[Reactor[Nothing]], Boolean)
}

trait Leveled{
  def level: Long
}

trait Id{

  val id: String = util.Random.alphanumeric.head.toString

  def debug(s: String) {
    println(id + ": " + s)
  }
}

