package rx

import scala.util.{Success, Try}
import scala.collection.mutable

trait Var[T] extends Rx[T] {
  def update(newValue: T): Unit

  def update(f: T => T):Unit = {
    update(f(now))
  }

  private[rx] var value: T

  private[rx] def depth: Int = 0

  def toTry: Try[T] = util.Success(now)

  override def recalc(): Unit = propagate()

  override def kill(): Unit = {
    clearDownstream()
  }

  def name: sourcecode.Name
  override def toString = s"${name.value}:Var@${Integer.toHexString(hashCode()).take(2)}($now)"
}

object Var {
  /**
    * Create a [[Var]] from an initial value
    */
  def apply[T](initialValue: T)(implicit name: sourcecode.Name): Var[T] = new Base(initialValue)

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
  class Base[T](initialValue: T)(implicit val name: sourcecode.Name) extends Var[T] {

    private[rx] val downStream = mutable.Set.empty[Rx.Dynamic[_]]
    private[rx] val observers = mutable.Set.empty[Obs]

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

  trait ForwardRxVar[T, S] extends Var[S] {
    protected val base: Var[T]
    protected val rx: Rx[S]

    // Proxy Rx
    override def now: S = rx.now

    override private[rx] def downStream = rx.downStream
    override private[rx] def observers = rx.observers

    override private[rx] def addDownstream(ctx: Ctx.Data): Unit = rx.addDownstream(ctx)
    override private[rx] def clearDownstream(): Unit = rx.clearDownstream()
    override private[rx] def depth = rx.depth

    override def kill() = {
      rx.kill()
      base.kill()
    }
  }

  class Composed[T](protected val base:Var[T], protected val rx:Rx[T])(implicit val name: sourcecode.Name) extends ForwardRxVar[T, T] {
    // Proxy Var
    private[rx] var value = now

    def update(newValue: T): Unit = {
      // We do a regular update of the base-var, since we do not know if
      // rx.now will be newValue
      base.update(newValue)
      value = rx.now
    }
  }

  class Isomorphic[T, S](protected val base: Var[T], read: T => S, write: S => T)(implicit ownerCtx: Ctx.Owner, val name: sourcecode.Name) extends ForwardRxVar[T, S] { self =>

    //  private[rx] val rx = base.map(read)
    protected val rx = Rx.build { (ownerCtx, dataCtx) =>
      base.addDownstream(dataCtx)
      read(base.now)
    }(ownerCtx, name)

    // Proxy Var
    override def update(newValue: S): Unit = {
      //TODO: ignore if already equal, like in BaseVar.update
      value = newValue
      // avoid triggering rx, because we already
      // know the current value: newValue
      Rx.doRecalc(
        rx.downStream ++ (base.downStream - rx),
        rx.observers ++ base.observers
      )
    }

    override private[rx] def value = rx.now

    override private[rx] def value_=(newValue: S): Unit = {
      rx.cached = Success(newValue)
      base.value = write(newValue)
      downStream ++= rx.downStream ++ (base.downStream - rx)
      observers ++=  rx.observers ++ base.observers
    }
  }


  class Zoomed[T, S](protected val base: Var[T], read: T => S, write: (T, S) => T)(implicit ownerCtx: Ctx.Owner, val name: sourcecode.Name) extends ForwardRxVar[T, S] {

    //  private[rx] val rx = base.map(read)
    protected val rx = Rx.build { (ownerCtx, dataCtx) =>
      base.addDownstream(dataCtx)
      read(base.now)
    }(ownerCtx, name)

    // Proxy Var
    override def update(newValue: S): Unit = {
      rx.cached = Success(newValue)
      base.value = write(base.value, newValue)

      // avoid triggering rx, because we already
      // know the current value: newValue
      Rx.doRecalc(
        rx.downStream ++ (base.downStream - rx),
        rx.observers ++ base.observers
      )
    }

    override private[rx] def value = rx.now

    override private[rx] def value_=(newValue: S): Unit = {
      rx.cached = Success(newValue)
      base.value = write(base.value, newValue)
      downStream ++= rx.downStream ++ (base.downStream - rx)
      observers ++=  rx.observers ++ base.observers
    }
  }

}
