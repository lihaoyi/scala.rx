package rx

import util.Try
import scala.util.{Failure, Success}
import rx.SyncSignals.DynamicSignal
import annotation.tailrec

import ref.WeakReference

import concurrent.{Future, ExecutionContext}
import java.util.concurrent.atomic.AtomicReference


/**
 * Contains all the basic traits which are used throughout the construction
 * of a dataflow graph
 */
object Flow{

  /**
   * A Signal is a value that can change over time, emitting pings whenever it
   * changes.
   *
   * This trait is normally accessed by its alias Rx
   */
  trait Signal[+T] extends Flow.Emitter[T] with Combinators.SignalMethods[T]{

    def currentValue: T = toTry.get

    def now: T = currentValue

    def apply(): T = {
      DynamicSignal.enclosing.value = DynamicSignal.enclosing.value match{
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
  trait Emitter[+T] extends Node{
    private[this] val children = Atomic[List[WeakReference[Reactor[T]]]](Nil)

    def getChildren: Seq[Reactor[Nothing]] = children.get.flatMap(_.get)

    def linkChild[R >: T](child: Reactor[R]): Unit = {
      children.spinSet(c => WeakReference(child) :: c.filter(_.get.isDefined))
    }
  }

  /**
   * Something that can receive pings
   */
  trait Reactor[-T] extends Node{

    def getParents: Seq[Emitter[Any]]

    def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]]

  }

  trait Node{
    def level: Long
    def name: String
    val id: String = util.Random.alphanumeric.head.toString

    def debug(s: String) {
      println(id + ": " + s)
    }
  }
}
