package rx

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable
import scala.util.Try

trait Node[T]{
  def value: T
  trait Internal {
    val downStream = mutable.Set.empty[Rx[_]]
    val observers = mutable.Set.empty[Obs]
    def depth: Int
  }
  def Internal: Internal
  def mark() = for(c <- Node.contextHead){
    Internal.downStream.add(c)
    c.Internal.depth = c.Internal.depth max (Internal.depth + 1)
  }
  def apply() = {
    mark()
    value
  }


  def trigger(thunk: => Unit) = {
    thunk
    Internal.observers.add(new Obs(() => thunk))
  }
  def triggerLater(thunk: => Unit) = {
    Internal.observers.add(new Obs(() => thunk))
  }
}
object Node{
  val contextStack = new ThreadLocal[mutable.Buffer[Rx[_]]]{
    override def initialValue = mutable.Buffer.empty[Rx[_]]
  }
  def contextHead = contextStack.get().lastOption
  def doRecalc(rxs: Iterable[Rx[_]], obs: Iterable[Obs]): Unit = {
    implicit val ordering = Ordering.by[Rx[_], Int](-_.Internal.depth)
    val queue = rxs.to[mutable.PriorityQueue]
    val seen = mutable.Set.empty[Int]
    val observers = obs.to[mutable.Set]
    while(queue.size > 0){
      val min = queue.dequeue()
      if (!seen(min.id)) {
        queue ++= min.Internal.downStream
        observers ++= min.Internal.observers
        min.update()
        seen.add(min.id)
      }
    }
    observers.foreach(_.thunk())
  }
}
object VarTuple{
  implicit def tuple2VarTuple[T](t: (Var[T], T)) = {
    VarTuple(t._1, t._2)
  }
  implicit def tuples2VarTuple[T](ts: Seq[(Var[T], T)]) = {
    ts.map(t => VarTuple(t._1, t._2))
  }
}
case class VarTuple[T](v: Var[T], value: T){
  def set() = v.value = value
}
object Var{
  def set(args: VarTuple[_]*) = {
    args.foreach(_.set())
    Node.doRecalc(
      args.flatMap(_.v.Internal.downStream),
      args.flatMap(_.v.Internal.observers)
    )
  }
}
case class Var[T](var value: T) extends Node[T]{
  object Internal extends Internal{
    def depth = 0
  }

  def update(newValue: T): Unit = {
    val toPing = Internal.downStream.toSet
    Internal.downStream.clear()
    value = newValue
    Node.doRecalc(toPing, Internal.observers)
  }
}

object Rx{
  val idCounter = new AtomicInteger
  def apply[T](func: => T) = new Rx(() => func)
}

case class Rx[T](func: () => T, id: Int = Rx.idCounter.getAndIncrement) extends Node[T] { r =>
  object Internal extends Internal{
    var depth = 0
    var dead = false
    val upStream = mutable.Set.empty[Node[_]]
    val owned = mutable.Set.empty[Node[_]]

  }
  var cached: Try[T] = {
    Node.contextHead.foreach(_.Internal.owned.add(r))
    calc()
  }
  def calc(): Try[T] = {
    Internal.downStream.clear()
    Internal.owned.clear()
    Node.contextStack.get().append(this)
    val r = Try(func())
    Node.contextStack.get().trimEnd(1)
    r
  }
  def value = cached.get
  def toTry = cached

  def kill() = {
    Internal.dead = true
    Internal.downStream.clear()
  }

  def update(): Unit = if (!Internal.dead) cached = calc()

  def recalc() = if (!Internal.dead) {
    update()
    Node.doRecalc(Internal.downStream, Internal.observers)
  }
}

case class Obs(thunk: () => Unit)