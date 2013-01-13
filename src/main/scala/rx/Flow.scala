package rx

import concurrent.{Await, Future}

import collection.mutable

import util.Try
import scala.util.{Failure, Success}
object Flow{
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
    def currentValue: T

    def now: T = currentValue

    def apply(): T = {
      val current = Sig.enclosing.value

      if (current != null){
        this.linkChild(Sig.enclosingR.value)
        Sig.enclosing.value = current.copy(
          level = math.max(this.level + 1, current.level),
          parents = this +: current.parents
        )
      }
      currentValue
    }

    def toTry: Try[T]
  }

  /**
   * Something that emits events. Manages a list of WeakReferences containing
   * listeners which need to be pinged when an event is fired.
   *
   * @tparam T The type of the events emitted
   */
  trait Emitter[+T] extends Node{

    private[this] val children: mutable.WeakHashMap[Reactor[T], Unit] = new mutable.WeakHashMap()

    def getChildren: Seq[Reactor[Nothing]] = children.keys.toSeq

    def linkChild[R >: T](child: Reactor[R]) = children(child) = ()
  }

  /**
   * Something that can receive events
   *
   * @tparam T The type of the event received
   */
  trait Reactor[-T] extends Node{

    def getParents: Seq[Emitter[Any]]

    def ping(incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]]
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
