package rx

import annotation.tailrec
import concurrent.{Future, ExecutionContext}



object Propagator{

  /**
   * A propagator which runs propagation waves on the given `ExecutionContext`.
   * Exactly how it is done (e.g. serially or in parallel) depends on the
   * `ExecutionContext` given. Returns a `Future` which will complete when
   * the propagation wave is finished. This `Future` can be blocked on if you
   * want to block until the propagation wave is complete before continuing.
   *
   * @param ec The `ExecutionContext` on which the distribute the individual
   *           updates of each propagation wave.
   */
  class Parallelizing(implicit ec: ExecutionContext) extends Propagator[Future[Unit]]{
    implicit val pinger = this
    def propagate(nodes: Seq[(Emitter[Any], Reactor[Nothing])]): Future[Unit] = {
      if (nodes.length != 0){
        val minLevel = nodes.map(_._2.level).min
        val (now, later) = nodes.partition(_._2.level == minLevel)
        val next = now.groupBy(_._2)
                      .mapValues(_.map(_._1).distinct)
                      .toSeq
                      .map{ case (target, pingers) => Future{
          target.ping(pingers).map(target.asInstanceOf[Emitter[Any]] -> _)
        }}
        Future.sequence[Seq[(Emitter[Any], Reactor[Nothing])], Seq](next).map(_.flatten ++ later).flatMap(propagate)
      }else Future.successful(())
    }
  }

  /**
   * A propagator which runs propagation waves on the thread which triggered
   * it. Returns `Unit` when the propagation wave is complete.
   */
  implicit object Immediate extends Propagator[Unit]{
    def propagate(nodes: Seq[(Emitter[Any], Reactor[Nothing])]): Unit = {
      if (nodes.length != 0){
        val minLevel = nodes.map(_._2.level).min
        val (now, later) = nodes.partition(_._2.level == minLevel)
        val next = now.groupBy(_._2)
                      .mapValues(_.map(_._1).distinct)
                      .toSeq
                      .map{ case (target, pingers) =>
          target.ping(pingers).map(target.asInstanceOf[Emitter[Any]] -> _)
        }
        propagate(next.flatten ++ later)
      }
    }
  }

  /**
   * Convenience method to retrieve the implicit [[Propagator]] from the
   * enclosing scope.
   */
  def apply[P: Propagator]() = implicitly[Propagator[P]]
}

/**
 * A Propagator is an object which performs a propagation wave over the
 * Scala.Rx dataflow graph. It is parametrized on the type P which it returns
 * after performing a propagation wave. The two existing Propagators are:
 *
 * - Immediate (`Propagator[Unit]`), which runs the propagation wave immediately
 *   and returns Unit when it is complete.
 * - Parallelizing (`Propagator[Future[Unit]]`), which runs the propagation wave
 *   on the given `ExecutionContext` and returns a `Future[Unit]` representing
 *   the completion of the propagation wave.
 *
 * It is conceivable that custom propagators could use the return type `P` to
 * return other things, e.g. the number of updates performed, or the number
 * of re-tries in that propagation wave. That is up to the implementer to
 * decide.
 *
 @tparam P the type that the `propagate()` function returns
 */
trait Propagator[P]{
  /**
   * Begins a propagation wave, with a set of
   *
   *     Emitter -> Reactor
   *
   * pings. See the implementation of [[Propagator.Immediate]] or
   * [[Propagator.Parallelizing]] to see how this is generally done
   *
   * @param pings The set of pings which begin this propagation wave
   * @return Some value of type P, dependent on the implementation
   */
  def propagate(pings: Seq[(Emitter[Any], Reactor[Nothing])]): P


}