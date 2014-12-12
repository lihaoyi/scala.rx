package rx
package ops
import acyclic.file
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

/**
 * A generic interface which can be used to schedule tasks.
 *
 * On the JVM this is an Akka `ActorSystem`, while in Javascript it is the
 * `setTimeout` function.
 */
trait Scheduler{
  def scheduleOnce[T](interval: FiniteDuration)
                     (thunk: => T)
                     (implicit executor: ExecutionContext)
}
