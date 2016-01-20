package rx

import rx.opmacros.Utils

import scala.reflect.internal.annotations.compileTimeOnly
import scala.language.experimental.macros
import scala.collection.mutable
import scala.reflect.macros.Context
import scala.util.Try

/**
  * A reactive value of type [[T]]. Keeps track of triggers and
  * other [[Node]]s that depend on it, running any triggers and notifying
  * downstream [[Node]]s when its value changes.
  */
sealed trait Node[+T] { self =>
  /**
    * Get the current value of this [[Node]] at this very moment,
    * without listening for updates
    */
  def now: T

  trait Internal {
    val downStream = mutable.Set.empty[Rx[_]]
    val observers = mutable.Set.empty[Obs]

    def clearDownstream() = Internal.downStream.clear()
    def depth: Int
    def addDownstream(ctx: Ctx.Data) = {
      downStream.add(ctx.rx)
      ctx.rx.Internal.upStream.add(self)
      ctx.rx.Internal.depth = ctx.rx.Internal.depth max (Internal.depth + 1)
    }
  }

  def Internal: Internal

  /**
    * Get the current value of this [[Node]] and listen for updates. Only
    * callable with an `Rx{...}` block (or equivalently when an implicit
    * [[Ctx.Data]] is available), and the contextual/implicit [[Rx]] is the
    * one that will update when the value of this [[Node]] changes.
    */
  def apply()(implicit ctx: Ctx.Data) = {
    Internal.addDownstream(ctx)
    now
  }

  /**
    * Kills this [[Node]]; stop listening for updates, and release all references
    * to other [[Node]]s. This lets the [[Node]] be garbage-collected, since otherwise
    * even when not-in-use it will continue to be referenced by the other [[Node]]s
    * it depends on.
    */
  def kill(): Unit

  /**
    * Force trigger/notifications of any downstream [[Node]]s, without changing the current value
    */
  def propagate(): Unit = Node.doRecalc(Internal.downStream.toSet, Internal.observers)

  /**
    * Run the given function immediately, and again whenever this [[Node]]s value
    * changes. Returns an [[Obs]] if you want to keep track of this trigger or
    * kill it later.
    */
  def trigger(thunk: => Unit) = {
    thunk
    triggerLater(thunk)
  }
  /**
    * Run the given function whenever this [[Node]]s value changes, but
    * not immediately. Returns an [[Obs]] if you want to keep track of this trigger or
    * kill it later.
    */
  def triggerLater(thunk: => Unit): Obs = {
    val o = new Obs(() => thunk, this)
    Internal.observers.add(o)
    o
  }

  def toRx(implicit ctx: Ctx.Owner): Rx[T]

  def toTry: Try[T]

  override def toString() = s"${super.toString}($now)"
}
object Node{
  def doRecalc(rxs: Iterable[Rx[_]], obs: Iterable[Obs]): Unit = {
    implicit val ordering = Ordering.by[Rx[_], Int](-_.Internal.depth)
    val queue = rxs.to[mutable.PriorityQueue]
    val seen = mutable.Set.empty[Node[_]]
    val observers = obs.to[mutable.Set]
    var currentDepth = 0
    while(queue.nonEmpty){
      val min = queue.dequeue()
      if (min.Internal.depth > currentDepth){
        currentDepth = min.Internal.depth
        seen.clear()
      }
      if (!seen(min) && !min.Internal.dead) {
        val prev = min.toTry
        min.Internal.update()
        if (min.toTry != prev){
          queue ++= min.Internal.downStream
          observers ++= min.Internal.observers
        }
        seen.add(min)
      }
    }
    observers.foreach(_.thunk())
  }
}

/**
  * Encapsulates the act of setting of a [[Var]] to a value, without
  * actually setting it.
  */
object VarTuple{
  implicit def tuple2VarTuple[T](t: (Var[T], T)): VarTuple[T] = {
    VarTuple(t._1, t._2)
  }

  implicit def tuples2VarTuple[T](ts: Seq[(Var[T], T)]): Seq[VarTuple[T]] = {
    ts.map(t => VarTuple(t._1, t._2))
  }
}
case class VarTuple[T](v: Var[T], value: T){
  def set() = v.Internal.value = value
}


object Var{
  /**
    * Create a [[Var]] from an initial value
    */
  def apply[T](initialValue: T) = new Var(initialValue)

  /**
    * Set the value of multiple [[Var]]s at the same time; in doing so,
    * reduces the redundant updates that would normally occur setting
    * them one by one
    */
  def set(args: VarTuple[_]*) = {
    args.foreach(_.set())
    Node.doRecalc(
      args.flatMap(_.v.Internal.downStream),
      args.flatMap(_.v.Internal.observers)
    )
  }

}
/**
  * A smart variable that can be set manually, and will notify downstream
  * [[Node]]s and run any triggers whenever its value changes.
  */
class Var[T](initialValue: T) extends Node[T]{

  object Internal extends Internal{
    def depth = 0
    var value = initialValue
  }

  override def now = Internal.value

  def toTry = util.Success(now)

  /**
    * Sets the value of this [[Var]] and runs any triggers/notifies
    * any downstream [[Node]]s to update
    */
  def update(newValue: T): Unit = {
    if (Internal.value != newValue) {
      Internal.value = newValue
      Node.doRecalc(Internal.downStream.toSet, Internal.observers)
    }
  }

  override def toRx(implicit ctx: Ctx.Owner): Rx[T] = Rx.build{
    (ownerCtx: Ctx.Owner, dataCtx: Ctx.Data) => apply()(dataCtx)
  }(ctx)

  override def kill() = {
    Internal.clearDownstream()
  }
}

object Rx{
  /**
    * Constructs a new [[Rx]] from an expression, that will be re-run any time
    * an upstream [[Node]] changes to re-calculate the value of this [[Rx]].
    *
    * Also injects an implicit [[Ctx.Owner]] into that block, which serves to keep
    * track of which other [[Node]]s are used within that block (via their
    * `apply` methods) so this [[Rx]] can recalculate when upstream changes.
    */
  def apply[T](func: => T)(implicit ownerCtx: Ctx.Owner, dataCtx: Ctx.Data): Rx[T] = macro Utils.buildMacro[T]

  def unsafe[T](func: => T): Rx[T] = macro Utils.buildUnsafe[T]

  /**
    * Constructs a new [[Rx]] from an expression (which explicitly takes an
    * [[Ctx.Owner]]) and an optional `owner` [[Ctx.Owner]].
    */
  def build[T](func: (Ctx.Owner, Ctx.Data) => T)(implicit owner: Ctx.Owner): Rx[T] = {
    require(owner != null, "owning RxCtx was null! Perhaps mark the caller lazy?")
    new Rx(func, if(owner == Ctx.Owner.Unsafe) None else Some(owner))
  }
}

/**
  * A [[Node]] that depends on other [[Node]]s, updating automatically
  * when their value changes. Optionally has an [[owner]], which is
  * another [[Rx]] this one was defined within. The [[Rx]] gets killed
  * automatically when the [[owner]] recalculates, in order to avoid
  * memory leaks from un-used [[Rx]]s hanging around.
  */
class Rx[+T](func: (Ctx.Owner, Ctx.Data) => T, owner: Option[Ctx.Owner]) extends Node[T] { self =>

  owner.foreach { o =>
    o.rx.Internal.owned.add(self)
    o.rx.Internal.addDownstream(new Ctx.Data(self))
  }

  private [this] var cached: Try[T] = null

  object Internal extends Internal{

    def owner = self.owner

    var depth = 0
    var dead = false
    val upStream = mutable.Set.empty[Node[_]]
    val owned = mutable.Set.empty[Rx[_]]

    override def clearDownstream() = {
      Internal.downStream.foreach(_.Internal.upStream.remove(self))
      Internal.downStream.clear()
    }

    def clearUpstream() = {
      Internal.upStream.foreach(_.Internal.downStream.remove(self))
      Internal.upStream.clear()
    }

    def calc(): Try[T] = {
      Internal.clearUpstream()
      Internal.owned.foreach(_.ownerKilled())
      Internal.owned.clear()
      Try(func(new Ctx.Owner(self), new Ctx.Data(self)))
    }

    def update() = {
      cached = calc()
    }
  }

  Internal.update()

  override def now = cached.get

  /**
    * @return the current value of this [[Rx]] as a `Try`
    */
  def toTry = cached

  def ownerKilled(): Unit = {
    Internal.dead = true
    Internal.clearDownstream()
    Internal.clearUpstream()
    Internal.owned.foreach(_.ownerKilled())
    Internal.owned.clear()
  }

  override def toRx(implicit ctx: Ctx.Owner): Rx[T] = Rx.build {
    (ownerCtx: Ctx.Owner, dataCtx: Ctx.Data) => this.apply()(dataCtx)
  }(ctx)

  override def kill(): Unit = {
    owner.foreach(_.rx.Internal.owned.remove(this))
    ownerKilled()
  }

  /**
    * Force this [[Rx]] to recompute (whether or not any upstream [[Node]]s
    * changed) and propagate changes downstream. Does nothing if the [[Rx]]
    * has been [[kill]]ed
    */
  def recalc(): Unit = if (!Internal.dead) {
    val oldValue = toTry
    Internal.update()
    if (oldValue != toTry)
      Node.doRecalc(Internal.downStream, Internal.observers)
  }
}

object Ctx{

  object Data extends Generic[Data]{
    @compileTimeOnly("No implicit Ctx.Data is available here!")
    object CompileTime extends Data(throw new Exception())

    object Unsafe extends Data(throw new Exception(
      "Invalid Ctx.Data: you can only call `Rx.apply` within an " +
        "`Rx{...}` block or where an implicit `RxCtx` is available"
    ))
    /**
      * Dark magic. End result is the implicit ctx will be one of
      *  1) The enclosing RxCtx, if it exists
      *  2) RxCtx.Unsafe, if in a "static context"
      *  3) RxCtx.CompileTime, if in a "dynamic context" (other macros will rewrite CompileTime away)
      */
    @compileTimeOnly("@}}>---: A rose by any other name.")
    implicit def voodoo: Data = macro Utils.voodooRxCtx[rx.Ctx.Data]
  }
  class Data(rx0: => Rx[_]) extends Ctx(rx0)
  object Owner extends Generic[Owner]{
    @compileTimeOnly("No implicit Ctx.Owner is available here!")
    object CompileTime extends Owner(throw new Exception())

    object Unsafe extends Owner(throw new Exception(
      "Invalid Ctx.Owner: you can only call `Rx.apply` within an " +
        "`Rx{...}` block or where an implicit `RxCtx` is available"
    ))
    /**
      * Dark magic. End result is the implicit ctx will be one of
      *  1) The enclosing RxCtx, if it exists
      *  2) RxCtx.Unsafe, if in a "static context"
      *  3) RxCtx.CompileTime, if in a "dynamic context" (other macros will rewrite CompileTime away)
      */
    @compileTimeOnly("@}}>---: A rose by any other name.")
    implicit def voodoo: Owner = macro Utils.voodooRxCtx[rx.Ctx.Owner]
  }
  class Owner(rx0: => Rx[_]) extends Ctx(rx0)

  class Generic[T] {
    def safe(): T = macro Utils.buildSafeCtx[T]
  }
}
/**
  * An implicit scope representing a "currently evaluating" [[Rx]]. Used to keep
  * track of dependencies or ownership.
  */
class Ctx(rx0: => Rx[_]){
  lazy val rx = rx0
}


/**
  * Wraps a simple callback, created by `trigger`, that fires when that
  * [[Node]] changes.
  */
class Obs(val thunk: () => Unit, upstream: Node[_]){
  object Internal{
    var dead = false
  }

  /**
    * Stop this observer from triggering and allow it to be garbage-collected
    */
  def kill() = {
    upstream.Internal.observers.remove(this)
    Internal.dead = true
  }
}