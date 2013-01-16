package rx

import concurrent.{Await, Future}

import collection.mutable

import util.Try
import scala.util.{Failure, Success}
import java.util.concurrent.atomic.AtomicReference
import rx.SyncSignals.DynamicSignal

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
  trait Signal[+T] extends Flow.Emitter[T]{

    def currentValue: T

    def now: T = currentValue

    def apply(): T = {
      val current = DynamicSignal.enclosing.value

      if (current != null){
        this.linkChild(DynamicSignal.enclosingR.value)
        DynamicSignal.enclosing.value = current.copy(
          level = math.max(this.level + 1, current.level),
          parents = this +: current.parents
        )
      }
      currentValue
    }

    def toTry: Try[T]
  }


  abstract class Settable[+T](initValue: T) extends Signal[T]{

    def level = 0L

    private[this] val currentValueHolder = new AtomicReference[Try[T]](Success(initValue))
    def currentValue = toTry.get
    def toTry = currentValueHolder.get


    protected[this] def update(newValue: Try[T]): Unit = {
      if (newValue != toTry){
        currentValueHolder.set(newValue)
        propagate(this.getChildren.map(this -> _))
      }
    }

    protected[this] def update(newValue: T): Unit = {
      if (Success(newValue) != toTry){
        currentValueHolder.set(Success(newValue))
        propagate(this.getChildren.map(this -> _))
      }
    }

    protected[this] def update(calc: T => T): Unit = {
      val oldValue = currentValue
      val newValue = calc(oldValue)
      if(!currentValueHolder.compareAndSet(Success(oldValue), Success(newValue))) update(calc)
      propagate(this.getChildren.map(this -> _))
    }
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

    def getEmitter: Emitter[T] = this
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
