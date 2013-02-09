package rx

import annotation.tailrec
import concurrent.{Future, ExecutionContext}

class BreadthFirstPropagator(val executionContext: ExecutionContext) extends Propagator{
  implicit val ec = executionContext

  def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Future[Unit] = {
    if (nodes.length != 0){
      val minLevel = nodes.minBy(_._2.level)._2.level
      val (now, later) = nodes.partition(_._2.level == minLevel)
      val next = for {
        (target, pingers) <- now.groupBy(_._2)
                                .mapValues(_.map(_._1).distinct)
                                .toSeq
      } yield {
        target.ping(pingers).map(_.map(target.asInstanceOf[Flow.Emitter[Any]] -> _))
      }
      Future.sequence(next).map(_.flatten ++ later).flatMap(propagate)
    } else {
      Future.successful(())
    }
  }
}

object Propagator{
  implicit object Immediate extends BreadthFirstPropagator(
    new ExecutionContext {
      def reportFailure(t: Throwable) { t.printStackTrace() }
      def execute(runnable: Runnable) {runnable.run()}
    }
  )

}
trait Propagator{
  def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Future[Unit]
  implicit def executionContext: ExecutionContext
}