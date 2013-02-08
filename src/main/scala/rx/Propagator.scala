package rx

import annotation.tailrec
import concurrent.ExecutionContext

object Propagator{
  implicit object Immediate extends Propagator{
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
    def executionContext = new ExecutionContext {
      def reportFailure(t: Throwable) { t.printStackTrace() }
      def execute(runnable: Runnable) {runnable.run()}
    }
  }

}
trait Propagator{
  def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Unit
  implicit def executionContext: ExecutionContext
}