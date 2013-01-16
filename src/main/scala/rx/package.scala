

import annotation.tailrec
import concurrent.Future
import rx.SyncSignals.DynamicSignal


package object rx {
  import Flow.Signal
  object NoInitializedException extends Exception()

  @tailrec def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Unit = {
    if (nodes.length != 0){
      val minLevel = nodes.minBy(_._2.level)._2.level
      val (now, later) = nodes.partition(_._2.level == minLevel)
      val next = for{
        (target, pingers) <- now.groupBy(_._2).mapValues(_.map(_._1).distinct).toSeq
        nextTarget <- target.ping(pingers)
      } yield {
        target.asInstanceOf[Flow.Emitter[Any]] -> nextTarget
      }

      propagate(next ++ later)
    }
  }

  type Rx[T] = Signal[T]
  val Rx = DynamicSignal
  implicit def pimpedSig[T](source: Rx[T]) = Combinators.pimpedSig(source)
  implicit def pimpedFutureSig[T](source: Rx[Future[T]]) = Combinators.pimpedFutureSig(source)
}

