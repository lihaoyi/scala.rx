package rx


import java.util.concurrent.Semaphore

import annotation.tailrec
import java.util.concurrent.atomic.AtomicBoolean
import concurrent.Promise

/**
 * Contains a future that can be updated manually, triggering his children.
 */
object Var {
  def apply[T](value: T) = new Var(Promise.successful(value))
  def apply[T] = new Var(Promise[T])
}

/**
 * A Var is a Signal which can be changed manually via assignment. Setting
 * the future is thread-safe as the semantics are controlled by the `ready`
 * AtomicBoolean.
 *
 * @param currentValue The initial future of the Var
 * @tparam T The type of the future this Var contains
 */
class Var[T](var currentValue: Promise[T]) extends Signal[T] {
  val ready = new AtomicBoolean(currentValue.isCompleted)

  debug("initialized " + this.getClass.getName)

  def future = currentValue.future

  def level = 0L

  def update(newValue: T): Unit = {
    debug("updated " + newValue)
    val cv = currentValue
    if (ready.compareAndSet(false, true)) cv.success(newValue)
    else currentValue = Promise.successful(newValue)
    propagate(this.getChildren.map(_ -> Set(this: Emitter[Any])).toMap)
  }

}