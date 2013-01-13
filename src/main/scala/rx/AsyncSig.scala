package rx

import concurrent.{ExecutionContext, Future}
import java.util.concurrent.atomic.AtomicInteger

trait Filter[T]{
  def handleSend(): T
  def handleReceive(old: T)(thunk: => Unit): Unit
}
object Filter{
  object NullFilter extends Filter[Unit]{
    def handleSend() = ()
    def handleReceive(old: Unit)(thunk: => Unit) = thunk
  }
  object DiscardLate extends Filter[Int]{
    val sendIndex = new AtomicInteger(0)
    val receiveIndex = new AtomicInteger(0)

    def handleSend = sendIndex.getAndIncrement
    def handleReceive(old: Int)(thunk: => Unit) = {
      if (old < receiveIndex.get()){
      }else{
        receiveIndex.set(old)
        thunk
      }
    }
  }
}

object AsyncSig{
  def apply[T, F](default: T,
                  outFilter: Filter[F] = Filter.NullFilter)
                 (calc: => Future[T])
                 (implicit executor: ExecutionContext) = new AsyncSig("name", default, outFilter, () => calc)
}

class AsyncSig[+T, F](val name: String, default: T, outFilter: Filter[F], calc: () => Future[T])(implicit executor: ExecutionContext) extends Signal[T]{

  private[this] val outputVar = Var(default)
  private[this] val inputSig = Sig{
    val saved = outFilter.handleSend()
    calc().onComplete{ x =>
      outFilter.handleReceive(saved){
        outputVar() = x
      }
    }
  }

  def level = outputVar.level
  def currentValue = outputVar.currentValue
  def toTry = outputVar.toTry
  override def apply[A](): T =  outputVar.apply()
}