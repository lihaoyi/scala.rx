package rx

import concurrent.{ExecutionContext, Future}
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}
import util.Try
import concurrent.duration.{FiniteDuration, Deadline, Duration}
import akka.actor.{Actor, Cancellable, ActorSystem}
import rx.Flow.Reactor


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
      sendIndex.set(id)
    }
    def handleReceive(id: Long, value: Try[T]) = {
      if (id >= receiveIndex.get()){
        receiveIndex.set(id)
        outputVar() = value
      }
    }
  }
}
class DebouncedSig[+T](source: Signal[T], interval: FiniteDuration)
                      (implicit system: ActorSystem, ex: ExecutionContext)
extends Signal[T]{

  private[this] var nextTime = Deadline.now
  private[this] var lastOutput: Option[(Try[T], Cancellable)] = None

  private[this] val outputVar = Var(source.currentValue)

  val listener = Obs(source){

    def update(value: Try[T]): Unit = {
      if (Deadline.now > nextTime){
        outputVar() = value
        nextTime = Deadline.now + interval
      }else{
        for ((value, cancellable) <- lastOutput) cancellable.cancel()
        lastOutput = Some(source.toTry -> system.scheduler.scheduleOnce(interval)(update(value)))
      }
    }
    update(source.toTry)
  }

  override def getChildren = outputVar.getChildren

  override def linkChild[R >: T](child: Reactor[R]) = outputVar.linkChild(child)
  def level = source.level + 1
  def name = "debounced " + source.name
  def currentValue = outputVar.currentValue
  def toTry = outputVar.toTry
}
class AsyncSig[+T](default: T, source: Signal[Future[T]], target: T => Target[T])
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

  def name = "async " + source.name
  def level = thisTarget.level
  def currentValue = thisTarget.currentValue
  def toTry = thisTarget.toTry
  override def apply(): T = thisTarget.apply()
}