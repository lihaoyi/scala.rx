import scala.concurrent.{Future, ExecutionContext}
import annotation.tailrec
import concurrent.ExecutionContext
import util.continuations.cpsParam

package object rx {
  implicit object InternalCallbackExecutor extends ExecutionContext {
    override def execute(runnable: Runnable){ runnable.run() }
    override def reportFailure(t: Throwable){
      throw new IllegalStateException("problem in scala.concurrent internal callback", t)
    }
  }

  type cpsResult = cpsParam[Cont[Any], Cont[Any]]

  class FutureSignal[T](f: Future[T]) extends Signal[T]{
    def level = 0L
    def future = f
  }

  implicit def future2signal[T](f: Future[T]) = new FutureSignal(f)

  @tailrec def propagate(nodes: Map[Reactor[Nothing], Set[Emitter[Any]]], minLevel: Long = -10): Unit = {
    val remainder = nodes.keys.filter(_.level > minLevel)
    if (remainder.isEmpty) ()
    else {
      val level = remainder.minBy(_.level).level

      val (nextNodes, laterNodes) = nodes.partition(_._1.level == level)

      val pingResults = nextNodes.map { case (node, pingers) =>
        (node.ping(pingers.toSeq), node)
      }

      val allBlocked = pingResults.forall{ case ((children, blocked), node) => blocked }

      if (allBlocked) {
        propagate(nodes, minLevel = level)
      } else {
        val nextNextNodes = pingResults.flatMap { case ((children, blocked), node) =>
          children.map(_ -> Set(node.asInstanceOf[Emitter[Any]]))
        }.toMap

        propagate(mergeMap(nextNextNodes, laterNodes)(_&_))
      }
    }
  }

  /**
   * Merges two maps of the same type into a third map of the same type. Where
   * the keys overlap, merge the values using the specified merge function.
   *
   * @param mA is the first map to merge
   * @param mB is the second map to merge
   * @param f is the function used to merge values in case of collision
   * @tparam A the type of the keys of the various maps
   * @tparam B the type of the values of the various maps
   * @return a new map containing the merger of the two maps
   */
  def mergeMap[A, B](mA: Map[A, B], mB: Map[A, B])(f: (B, B) => B): Map[A, B] =
    (mA.keySet | mB.keySet).flatMap{ x =>
      val values = (mA.get(x), mB.get(x)) match {
        case (Some(a), Some(b)) => Some(f(a, b))
        case (Some(a), None) => Some(a)
        case (None, Some(b)) => Some(b)
        case (None, None) => None
      }
      values.map(x -> _)
    }.toMap

  def doubleCheckedSync[A](lock: AnyRef, condition: => Boolean)(body: => A)(default: A) = {
    if(condition){
      lock.synchronized[A]{
        if(condition) body
        else default
      }
    }else default
  }
}

