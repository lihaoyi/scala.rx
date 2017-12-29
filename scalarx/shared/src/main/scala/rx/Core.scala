package rx

import rx.opmacros.Factories

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.collection.mutable
import scala.util.Try

/**
  * A reactive value of type [[T]]. Keeps track of triggers and
  * other [[Rx]]s that depend on it, running any triggers and notifying
  * downstream [[Rx]]s when its value changes.
  */
sealed trait Rx[+T] { self =>
  /**
    * Get the current value of this [[Rx]] at this very moment,
    * without listening for updates
    */
  def now: T

  trait Internal {
    val downStream = mutable.Set.empty[Rx.Dynamic[_]]
    val observers = mutable.Set.empty[Obs]

    def clearDownstream(): Unit = Internal.downStream.clear()
    def depth: Int
    def addDownstream(ctx: Ctx.Data): Unit = {
      downStream.add(ctx.contextualRx)
      ctx.contextualRx.Internal.upStream.add(self)
      ctx.contextualRx.Internal.depth = ctx.contextualRx.Internal.depth max (Internal.depth + 1)
    }
  }

  def Internal: Internal

  /**
    * Get the current value of this [[Rx]] and listen for updates. Only
    * callable with an `Rx{...}` block (or equivalently when an implicit
    * [[Ctx.Data]] is available), and the contextual/implicit [[Rx]] is the
    * one that will update when the value of this [[Rx]] changes.
    */
  def apply()(implicit ctx: Ctx.Data): T = {
    Internal.addDownstream(ctx)
    now
  }

  /**
    * Kills this [[Rx]]; stop listening for updates, and release all references
    * to other [[Rx]]s. This lets the [[Rx]] be garbage-collected, since otherwise
    * even when not-in-use it will continue to be referenced by the other [[Rx]]s
    * it depends on.
    */
  def kill(): Unit

  /**
    * Force trigger/notifications of any downstream [[Rx]]s, without changing the current value
    */
  def propagate(): Unit = Rx.doRecalc(Internal.downStream.toSet, Internal.observers)

  /**
    * Force this [[Rx]] to recompute (whether or not any upstream [[Rx]]s
    * changed) and propagate changes downstream. Does nothing if the [[Rx]]
    * has been [[kill]]ed
    */
  def recalc(): Unit

  /**
    * Run the given function immediately, and again whenever this [[Rx]]s value
    * changes. Returns an [[Obs]] if you want to keep track of this trigger or
    * kill it later.
    */
  def trigger(thunk: => Unit)(implicit ownerCtx: rx.Ctx.Owner): Obs = {
    thunk
    triggerLater(thunk)
  }
  /**
    * Run the given function whenever this [[Rx]]s value changes, but
    * not immediately. Returns an [[Obs]] if you want to keep track of this trigger or
    * kill it later.
    */
  def triggerLater(thunk: => Unit)(implicit ownerCtx: rx.Ctx.Owner): Obs = {
    val o = new Obs(() => thunk, this)
    if(ownerCtx != Ctx.Owner.Unsafe) {
      ownerCtx.contextualRx.Internal.ownedObservers.add(o)
    }
    Internal.observers.add(o)
    o
  }

  def toTry: Try[T]
}



object Rx{
  /**
    * Constructs a new [[Rx]] from an expression, that will be re-run any time
    * an upstream [[Rx]] changes to re-calculate the value of this [[Rx]].
    *
    * Also injects an implicit [[Ctx.Owner]] into that block, which serves to keep
    * track of which other [[Rx]]s are used within that block (via their
    * `apply` methods) so this [[Rx]] can recalculate when upstream changes.
    */
  def apply[T](func: => T)(implicit ownerCtx: rx.Ctx.Owner): Rx.Dynamic[T] = macro Factories.rxApplyMacro[T]

  def unsafe[T](func: => T): Rx[T] = macro Factories.buildUnsafe[T]

  /**
    * Constructs a new [[Rx]] from an expression (which explicitly takes an
    * [[Ctx.Owner]]) and an optional `owner` [[Ctx.Owner]].
    */
  def build[T](func: (Ctx.Owner, Ctx.Data) => T)(implicit owner: Ctx.Owner): Rx.Dynamic[T] = {
    require(owner != null, "owning RxCtx was null! Perhaps mark the caller lazy?")
    new Rx.Dynamic(func, if(owner == Ctx.Owner.Unsafe) None else Some(owner))
  }

  def doRecalc(rxs: Iterable[Rx.Dynamic[_]], obs: Iterable[Obs]): Unit = {
    implicit val ordering: Ordering[Rx.Dynamic[_]] = Ordering.by[Rx.Dynamic[_], Int](-_.Internal.depth)
    val queue = rxs.to[mutable.PriorityQueue]
    val seen = mutable.Set.empty[Rx.Dynamic[_]]
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
    observers.filter(!_.Internal.dead).foreach(_.thunk())
  }

  /**
    * A [[Rx]] that depends on other [[Rx]]s, updating automatically
    * when their value changes. Optionally has an [[owner]], which is
    * another [[Rx]] this one was defined within. The [[Rx]] gets killed
    * automatically when the [[owner]] recalculates, in order to avoid
    * memory leaks from un-used [[Rx]]s hanging around.
    */
  class Dynamic[+T](func: (Ctx.Owner, Ctx.Data) => T, owner: Option[Ctx.Owner]) extends Rx[T] { self =>

    owner.foreach { o =>
      o.contextualRx.Internal.owned.add(self)
      o.contextualRx.Internal.addDownstream(new Ctx.Data(self))
    }

    private [this] var cached: Try[T] = null

    object Internal extends Internal{

      def owner = self.owner

      var depth = 0
      var dead = false
      val upStream = mutable.Set.empty[Rx[_]]
      val owned = mutable.Set.empty[Rx.Dynamic[_]]
      val ownedObservers = mutable.Set.empty[Obs]

      override def clearDownstream(): Unit = {
        Internal.downStream.foreach(_.Internal.upStream.remove(self))
        Internal.downStream.clear()
      }

      def clearOwned(): Unit  = {
        Internal.owned.foreach(_.ownerKilled())
        Internal.owned.clear()
        Internal.ownedObservers.foreach(_.kill())
        Internal.ownedObservers.clear()
      }

      def clearUpstream(): Unit  = {
        Internal.upStream.foreach(_.Internal.downStream.remove(self))
        Internal.upStream.clear()
      }

      def calc(): Try[T] = {
        Internal.clearUpstream()
        clearOwned()
        Try(func(new Ctx.Owner(self), new Ctx.Data(self)))
      }

      def update(): Unit  = {
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
      Internal.clearOwned()
    }

    override def kill(): Unit = {
      owner.foreach(_.contextualRx.Internal.owned.remove(this))
      ownerKilled()
    }

    override def recalc(): Unit = if (!Internal.dead) {
      val oldValue = toTry
      Internal.update()
      if (oldValue != toTry)
        Rx.doRecalc(Internal.downStream, Internal.observers)
    }

    override def toString() = s"Rx@${Integer.toHexString(hashCode()).take(2)}($now)"
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
  def set(): Unit = v.Internal.value = value
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
  def set(args: VarTuple[_]*): Unit = {
    args.foreach(_.set())
    Rx.doRecalc(
      args.flatMap(_.v.Internal.downStream),
      args.flatMap(_.v.Internal.observers)
    )
  }

}
/**
  * A smart variable that can be set manually, and will notify downstream
  * [[Rx]]s and run any triggers whenever its value changes.
  */
class Var[T](initialValue: T) extends Rx[T]{

  object Internal extends Internal{
    def depth = 0
    var value: T = initialValue
  }

  override def now: T = Internal.value

  def toTry = util.Success(now)

  /**
    * Sets the value of this [[Var]] and runs any triggers/notifies
    * any downstream [[Rx]]s to update
    */
  def update(newValue: T): Unit = {
    if (Internal.value != newValue) {
      Internal.value = newValue
      Rx.doRecalc(Internal.downStream.toSet, Internal.observers)
    }
  }

  override def recalc(): Unit = propagate()

  override def kill(): Unit = {
    Internal.clearDownstream()
  }

  override def toString: String = s"Var@${Integer.toHexString(hashCode()).take(2)}($now)"
}

object Ctx{

  object Data extends Generic[Data]{
    @compileTimeOnly("No implicit Ctx.Data is available here!")
    implicit object CompileTime extends Data(???)
  }
  class Data(rx0: => Rx.Dynamic[_]) extends Ctx(rx0)

  object Owner extends Generic[Owner]{
    @compileTimeOnly("No implicit Ctx.Owner is available here!")
    object CompileTime extends Owner(???)

    object Unsafe extends Owner(???){
      implicit val Unsafe: Ctx.Owner.Unsafe.type = this
    }
    /**
      * Dark magic. End result is the implicit ctx will be one of
      *  1) The enclosing RxCtx, if it exists
      *  2) RxCtx.Unsafe, if in a "static context"
      *  3) RxCtx.CompileTime, if in a "dynamic context" (other macros will rewrite CompileTime away)
      */
    @compileTimeOnly("@}}>---: A rose by any other name.")
    implicit def voodoo: Owner = macro Factories.automaticOwnerContext[rx.Ctx.Owner]
  }

  class Owner(rx0: => Rx.Dynamic[_]) extends Ctx(rx0)

  class Generic[T] {
    def safe(): T = macro Factories.buildSafeCtx[T]
  }
}
/**
  * An implicit scope representing a "currently evaluating" [[Rx]]. Used to keep
  * track of dependencies or ownership.
  */
class Ctx(rx0: => Rx.Dynamic[_]){
  lazy val contextualRx = rx0
}


/**
  * Wraps a simple callback, created by `trigger`, that fires when that
  * [[Rx]] changes.
  */
class Obs(val thunk: () => Unit, upstream: Rx[_]){
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