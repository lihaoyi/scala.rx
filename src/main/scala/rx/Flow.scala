package rx

import util.Try
import scala.util.{Failure, Success}

import annotation.tailrec

import ref.WeakReference

import concurrent.{Future, ExecutionContext}
import java.util.concurrent.atomic.AtomicReference



/**
 * A Signal is a value that can change over time, emitting pings whenever it
 * changes.
 *
 * This trait is normally accessed by its alias Signal
 */
trait Signal[+T] extends Emitter[T] with utility.SignalMethods[T]{

  def currentValue: T = toTry.get

  def now: T = currentValue

  def apply(): T = {

    Dynamic.enclosing.value = Dynamic.enclosing.value match{
      case Some((enclosing, dependees)) =>
        this.linkChild(enclosing)
        Some((enclosing, this :: dependees))
      case None => None
    }
    currentValue
  }

  def propagate[P: Propagator]() = {
    Propagator().propagate(this.getChildren.map(this -> _))
  }

  def toTry: Try[T]
}

/**
 * Something that emits pings. Manages a list of WeakReferences containing
 * listeners which need to be pinged when an event is fired.
 */
trait Emitter[+T] extends utility.Node{
  private[this] val children = Atomic[List[WeakReference[Reactor[T]]]](Nil)

  def getChildren: Seq[Reactor[Nothing]] = children.get.flatMap(_.get)

  def linkChild[R >: T](child: Reactor[R]): Unit = {
    children.spinSet(c => (WeakReference(child) :: c.filter(_.get.isDefined)).distinct)
  }
}

/**
 * Something that can receive pings and react to them in some way. How it reacts
 * is up to the implementation.
 */
trait Reactor[-T] extends utility.Node{

  def getParents: Seq[Emitter[Any]]

  /**
   * Pings this [[Reactor]] with some [[Emitter]]s, causing it to react.
   */
  def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]]

}



