package rx

import annotation.tailrec
import concurrent.{Future, ExecutionContext}



object Propagator{


  class Parallelizing(implicit ec: ExecutionContext) extends Propagator[Future[Unit]]{
    def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Future[Unit] = {
      if (nodes.length != 0){
        val minLevel = nodes.map(_._2.level).min
        val (now, later) = nodes.partition(_._2.level == minLevel)
        val next = now.groupBy(_._2)
          .mapValues(_.map(_._1).distinct)
          .toSeq
          .map{ case (target, pingers) => Future{
          target.ping(pingers).map(target.asInstanceOf[Flow.Emitter[Any]] -> _)
        }}
        Future.sequence(next).map(_.flatten ++ later).flatMap(propagate)
      }else Future.successful(())
    }
  }
  implicit object Immediate extends Propagator[Unit]{

    def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Unit = {
      if (nodes.length != 0){
        val minLevel = nodes.map(_._2.level).min
        val (now, later) = nodes.partition(_._2.level == minLevel)
        val next = now.groupBy(_._2)
          .mapValues(_.map(_._1).distinct)
          .toSeq
          .map{ case (target, pingers) =>
          target.ping(pingers).map(target.asInstanceOf[Flow.Emitter[Any]] -> _)
        }
        propagate(next.flatten ++ later)
      }
    }
  }
  def apply[P: Propagator]() = implicitly[Propagator[P]]
}

trait Propagator[P]{
  def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): P
}