import java.util.concurrent.atomic.AtomicInteger

import collection.mutable
import scala.util.Try

package object rx {
  val contextStack = new ThreadLocal[mutable.Buffer[Rx[_]]]{
    override def initialValue = mutable.Buffer.empty[Rx[_]]
  }
  val idCounter = new AtomicInteger
  trait Node[T]{
    def value: T
    val downStream = mutable.Set.empty[Rx[_]]
    def apply() = {
      contextStack.get()
                  .headOption
                  .foreach(downStream.add)
      value
    }
    def depth: Int
    def recalc() = doRecalc(this.downStream.toSet)
    def calcSet: Seq[Rx[_]]
  }
  case class Var[T](var value: T) extends Node[T]{

    def depth = 0
    def update(newValue: T): Unit = {
      val toPing = downStream.toSet
      downStream.clear()
      value = newValue
      doRecalc(toPing)

      value = newValue
    }
    def calcSet(): Seq[Rx[_]] = downStream.toSeq
  }
  
  object Rx{
    def apply[T](func: => T) = new Rx(() => func)
  }

  case class Rx[T](func: () => T, id: Int = idCounter.getAndIncrement) extends Node[T] {
    var depth = 0
    val upStream = mutable.Set.empty[Node[_]]
    val owned = mutable.Set.empty[Node[_]]
    def calc(): (Try[T], Seq[Rx[_]]) = {
      val toPing = downStream.toArray
      downStream.clear()
      owned.clear()
      contextStack.get()
                  .headOption
                  .foreach(_.downStream.add(this))
      contextStack.get().append(this)
      val r = Try(func())
      contextStack.get().trimEnd(1)
      (r, toPing)
    }
    def value = cached.get

    var cached: Try[T] = {
      contextStack.get()
                  .headOption
                  .foreach(_.owned.add(this))
      calc()._1
    }

    def kill() = {
      upStream.foreach(_.downStream.remove(this))
      upStream.clear()
    }

    def calcSet(): Seq[Rx[_]] = {
      val (r, next) = calc()
      cached = r
      next
    }
  }

  def doRecalc(rxs: Set[Rx[_]]): Unit = {
    val front = mutable.Buffer[Rx[_]](rxs.toSeq:_*)
    while(front.length > 0){
      val (shallowest, rest) =
        front.partition(_.depth == front.minBy(_.depth).depth)
      front.clear()
      front.appendAll(rest)
      for(rx <- shallowest){
        front.appendAll(rx.calcSet())
      }
    }
  }
}

