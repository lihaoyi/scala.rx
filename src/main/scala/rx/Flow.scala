package rx

import concurrent.{Await, Future}

import collection.mutable

import util.Try
import scala.util.{Failure, Success}
object Flow{

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
