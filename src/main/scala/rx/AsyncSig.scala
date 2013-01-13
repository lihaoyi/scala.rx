package rx

import concurrent.{ExecutionContext, Future}
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import util.Try


abstract class Target[T](default: T) extends Signal[T]{

  val outputVar = Var(default)
  def handleSend(id: Long): Unit
  def handleReceive(id: Long, value: Try[T]): Unit

  override def apply() = outputVar.apply()
  def level = outputVar.level
  def name = outputVar.name
  def currentValue = outputVar.currentValue
  def toTry = outputVar.toTry
}
//object AsyncCombinators extends AsyncCombinators
object AsyncCombinators{
  implicit class pimpedAsyncSig[T](source: AsyncSig[T]){
    def discardLate = DiscardLate(source.currentValue)
  }
  case class BaseTarget[T](default: T) extends Target[T](default){
    def handleSend(id: Long) = ()
    def handleReceive(id: Long, value: Try[T]) = outputVar() = value
  }
  case class DiscardLate[T](default: T) extends Target[T](default){
    val sendIndex = new AtomicLong(0)
    val receiveIndex = new AtomicLong(0)

    def handleSend(id: Long) = {
      println("Handle Send " + id)
      sendIndex.set(id)
    }
    def handleReceive(id: Long, value: Try[T]) = {
      println("Handle Receive " + id)
      if (id >= receiveIndex.get()){
        receiveIndex.set(id)
        outputVar() = value
      }
    }
  }
}



class AsyncSig[+T](val name: String, default: T, source: Signal[Future[T]], target: T => Target[T])
                  (implicit executor: ExecutionContext)
extends Signal[T]{

  val count = new AtomicLong(0)
  private[this] val thisTarget = target(default)
  private[this] var targets = Seq(thisTarget)
  private[this] val listener = Obs(source){
    val future = source()
    val id = count.getAndIncrement
    targets.foreach(_.handleSend(id))
    future.onComplete{ x =>
      targets.foreach(_.handleReceive(id, x))
    }
  }
  listener.trigger()

  def level = thisTarget.level
  def currentValue = thisTarget.currentValue
  def toTry = thisTarget.toTry
  override def apply(): T = thisTarget.apply()
}
