package rx

import scala.util.Try

trait Var[T] extends Rx[T] {
  def update(newValue: T): Unit

  def update(f: T => T):Unit = {
    update(f(now))
  }

  private[rx] var value: T

  private[rx] def depth: Int = 0

  def toTry: Try[T] = util.Success(now)

  private[rx] override def recalc(): Unit = propagate()

  private[rx] override def kill(): Unit = {
    clearDownstream()
  }

  override def toString = s"Var@${Integer.toHexString(hashCode()).take(2)}($now)"
}

object Var {
  /**
    * Create a [[Var]] from an initial value
    */
  def apply[T](initialValue: T): Var[T] = new Base(initialValue)

  /**
    * Set the value of multiple [[Var]]s at the same time; in doing so,
    * reduces the redundant updates that would normally occur setting
    * them one by one
    */
  def set(args: Assignment[_]*): Unit = {
    args.foreach(_.set())
    Rx.doRecalc(
      args.flatMap(_.v.downStream),
      args.flatMap(_.v.observers)
    )
  }

  case class Assignment[T](v: Var[T], value: T) {
    def set(): Unit = {
      v.value = value
    }
  }

  /**
    * Encapsulates the act of setting of a [[Var]] to a value, without
    * actually setting it.
    */
  object Assignment {
    implicit def tuple2Assignment[T](t: (Var[T], T)): Assignment[T] = {
      Assignment(t._1, t._2)
    }

    implicit def tuples2Assignments[T](ts: Seq[(Var[T], T)]): Seq[Assignment[T]] = {
      ts.map(t => Assignment(t._1, t._2))
    }
  }

  /**
    * A smart variable that can be set manually, and will notify downstream
    * [[Rx]]s and run any triggers whenever its value changes.
    */
  class Base[T](initialValue: T) extends Var[T] {

    private[rx] var value = initialValue

    override def now: T = value

    /**
      * Sets the value of this [[Var]] and runs any triggers/notifies
      * any downstream [[Rx]]s to update
      */
    def update(newValue: T): Unit = {
      if (value != newValue) {
        value = newValue
        Rx.doRecalc(downStream, observers)
      }
    }
  }
}


