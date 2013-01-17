package rx

import concurrent.{Await, Future}

import collection.mutable

import util.Try
import scala.util.{Failure, Success}
import java.util.concurrent.atomic.AtomicReference
import rx.SyncSignals.DynamicSignal
import annotation.tailrec
import concurrent.stm._
import java.security.cert.TrustAnchor
import ref.WeakReference

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
      DynamicSignal.enclosing.value match{
        case Some(enclosing) =>
          implicit val (current, txn) = enclosing
          this.linkChild(current)
          current.incrementLevel(this.level + 1)
          current.addParent(this)
        case None => ()
      }

      currentValue
    }

    def propagate() = {
      Signal.propagate(this.getChildren.map(this -> _))
    }

    def toTry: Try[T]
  }
  object Signal{
    @tailrec def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Unit = {
      if (nodes.length != 0){
        val minLevel = nodes.minBy(_._2.level)._2.level
        val (now, later) = nodes.partition(_._2.level == minLevel)
        val next = for {
          (target, pingers) <- now.groupBy(_._2)
                                  .mapValues(_.map(_._1).distinct)
                                  .toSeq
          nextTarget <- target.ping(pingers)
        } yield {
          target.asInstanceOf[Flow.Emitter[Any]] -> nextTarget
        }
        propagate(next ++ later)
      }
    }
  }

  /**
   * Something which contains an initial value and who can update (its own)
   * value, pinging its children.
   */
  abstract class Settable[+T](initValue: T) extends Signal[T]{

    def level = 0L

    private[this] val currentValueHolder = Ref[Try[T]](Success(initValue))

    def toTry = currentValueHolder.single()

    protected[this] def updateS(newValue: Try[T]): Unit = {
      if (newValue != toTry){
        if (!atomic{ implicit txn =>
          val old = currentValueHolder.single()
          currentValueHolder.single() = newValue
          old == newValue
        }) propagate()
      }
    }

    protected[this] def updateS(newValue: T): Unit = updateS(Success(newValue))

    protected[this] def updateS(calc: T => T): Unit = {
      if(!currentValueHolder.single.transformIfDefined{
        case Success(v) => Success(calc(v))
      }) propagate()
    }
  }


  /**
   * Something that emits pings. Manages a list of WeakReferences containing
   * listeners which need to be pinged when an event is fired.
   */
  trait Emitter[+T] extends Node{
    private[this] val children = Ref(Seq[WeakReference[Reactor[T]]]())

    def getChildren: Seq[Reactor[Nothing]] =
      children.single().flatMap(_.get)

    def linkChild[R >: T](child: Reactor[R]) =
      children.single.transform(_.filter(_.get.isDefined) :+ WeakReference(child))

  }

  /**
   * Something that can receive pings
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
