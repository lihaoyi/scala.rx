package rx

import concurrent.{ExecutionContext, Future}
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import util.Try
import rx.AsyncCombinators.BaseTarget

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


case class AsyncNode[T](target: Var[T])
extends Call.Emitter[T]
with Signal[T]
with Call.Reactor[T]{

  def pingChildren() = {
    target
  }

  def level = ???

  def currentValue = ???

  def toTry = ???

  def name = ???

  def update(msg: T) {}

  def update(msg: Try[T]) {}
}

object AsyncSig{
  def apply[T](default: T)
                 (calc: => Future[T])
                 (implicit executor: ExecutionContext) = {
    new AsyncSig("name", default, () => calc)
  }
}

class AsyncSig[+T](val name: String, default: T, calc: () => Future[T])
                  (implicit executor: ExecutionContext)
extends Signal[T]{

  val count = new AtomicLong(0)
  private[this] val thisTarget = BaseTarget(default)
  private[this] var targets = Seq(thisTarget)
  private[this] val inputSig = Sig{

    val id = count.getAndIncrement
    targets.foreach(_.handleSend(id))
    calc().onComplete{ x =>
      targets.foreach(_.handleReceive(id, x))
    }
  }

  def level = thisTarget.level
  def currentValue = thisTarget.currentValue
  def toTry = thisTarget.toTry
  override def apply(): T = thisTarget.apply()
}