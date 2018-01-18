package rx

import rx.opmacros.Factories

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.mutable
import scala.util.Try

/**
  * A reactive value of type [[T]]. Keeps track of triggers and
  * other [[Rx]]s that depend on it, running any triggers and notifying
  * downstream [[Rx]]s when its value changes.
  */
trait Rx[+T] {
  self =>
  /**
    * Get the current value of this [[Rx]] at this very moment,
    * without listening for updates
    */
  def now: T

  private[rx] val downStream = mutable.Set.empty[Rx.Dynamic[_]]
  private[rx] val observers = mutable.Set.empty[Obs]

  private[rx] def clearDownstream(): Unit = downStream.clear()

  private[rx] def depth: Int

  private[rx] def addDownstream(ctx: Ctx.Data): Unit = {
    downStream.add(ctx.contextualRx)
    ctx.contextualRx.upStream.add(self)
    ctx.contextualRx.depth = ctx.contextualRx.depth max (depth + 1)
  }

  /**
    * Get the current value of this [[Rx]] and listen for updates. Only
    * callable with an `Rx{...}` block (or equivalently when an implicit
    * [[Ctx.Data]] is available), and the contextual/implicit [[Rx]] is the
    * one that will update when the value of this [[Rx]] changes.
    */
  def apply()(implicit ctx: Ctx.Data): T = {
    addDownstream(ctx)
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
  def propagate(): Unit = Rx.doRecalc(downStream.toSet, observers)

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
    if (ownerCtx != Ctx.Owner.Unsafe) {
      ownerCtx.contextualRx.ownedObservers.add(o)
    }
    observers.add(o)
    o
  }

  def toTry: Try[T]
}

object Rx {
  /**
    * Constructs a new [[Rx]] from an expression, that will be re-run any time
    * an upstream [[Rx]] changes to re-calculate the value of this [[Rx]].
    *
    * Also injects an implicit [[Ctx.Owner]] into that block, which serves to keep
    * track of which other [[Rx]]s are used within that block (via their
    * `apply` methods) so this [[Rx]] can recalculate when upstream changes.
    */
  def apply[T](func: => T)(implicit ownerCtx: rx.Ctx.Owner, name: sourcecode.Name): Rx.Dynamic[T] = macro Factories.rxApplyMacro[T]

  private[rx] def unsafe[T](func: => T)(implicit name: sourcecode.Name): Rx[T] = macro Factories.buildUnsafe[T]

  /**
    * Constructs a new [[Rx]] from an expression (which explicitly takes an
    * [[Ctx.Owner]]) and an optional `owner` [[Ctx.Owner]].
    */
  def build[T](func: (Ctx.Owner, Ctx.Data) => T)(implicit owner: Ctx.Owner, name: sourcecode.Name): Rx.Dynamic[T] = {
    require(owner != null, "owning RxCtx was null! Perhaps mark the caller lazy?")
    new Rx.Dynamic(func, if (owner == Ctx.Owner.Unsafe) None else Some(owner))
  }

  private[rx] def doRecalc(rxs: Iterable[Rx.Dynamic[_]], obs: Iterable[Obs]): Unit = {
    implicit val ordering: Ordering[Dynamic[_]] = Ordering.by[Rx.Dynamic[_], Int](-_.depth)
    val queue = rxs.to[mutable.PriorityQueue]
    val seen = mutable.Set.empty[Rx.Dynamic[_]]
    val observers = obs.to[mutable.Set]
    var currentDepth = 0
    while (queue.nonEmpty) {
      val min = queue.dequeue()
      if(min.depth < currentDepth) {
        currentDepth = min.depth
      }
      else if (min.depth > currentDepth) {
        currentDepth = min.depth
        seen.clear()
      }
      if (!seen(min) && !min.dead) {
        val prev = min.toTry
        min.update()
        if (min.toTry != prev) {
          queue ++= min.downStream
          observers ++= min.observers
        }
        seen.add(min)
      }
    }
    observers.filter(!_.dead).foreach(_.thunk())
  }

  /**
    * A [[Rx]] that depends on other [[Rx]]s, updating automatically
    * when their value changes. Optionally has an [[owner]], which is
    * another [[Rx]] this one was defined within. The [[Rx]] gets killed
    * automatically when the [[owner]] recalculates, in order to avoid
    * memory leaks from un-used [[Rx]]s hanging around.
    */
  class Dynamic[+T](func: (Ctx.Owner, Ctx.Data) => T, owner: Option[Ctx.Owner])(implicit name: sourcecode.Name) extends Rx[T] { self =>

    private[rx] var cached: Try[T @uncheckedVariance] = _

    private[rx] var depth = 0
    private[rx] var dead = false
    private[rx] val upStream = mutable.Set.empty[Rx[_]]
    private[rx] val owned = mutable.Set.empty[Rx.Dynamic[_]]
    private[rx] val ownedObservers = mutable.Set.empty[Obs]

    override private[rx] def clearDownstream(): Unit = {
      downStream.foreach(_.upStream.remove(self))
      downStream.clear()
    }

    private[rx] def clearOwned(): Unit = {
      owned.foreach(_.ownerKilled())
      owned.clear()
      ownedObservers.foreach(_.kill())
      ownedObservers.clear()
    }

    private[rx] def clearUpstream(): Unit = {
      upStream.foreach(_.downStream.remove(self))
      upStream.clear()
    }

    private[rx] def calc(): Try[T] = {
      clearUpstream()
      clearOwned()
      Try(func(new Ctx.Owner(self), new Ctx.Data(self)))
    }

    private[rx] def update(): Unit = {
      cached = calc()
    }

    owner.foreach { o =>
      o.contextualRx.owned.add(self)
      o.contextualRx.addDownstream(new Ctx.Data(self))
    }

    update()

    override def now: T = cached.get

    /**
      * @return the current value of this [[Rx]] as a `Try`
      */
    def toTry: Try[T] = cached

    private[rx] def ownerKilled(): Unit = {
      dead = true
      clearDownstream()
      clearUpstream()
      clearOwned()
    }

    override def kill(): Unit = {
      owner.foreach(_.contextualRx.owned.remove(this))
      ownerKilled()
    }

    override def recalc(): Unit = if (!dead) {
      val oldValue = toTry
      update()
      if (oldValue != toTry)
        Rx.doRecalc(downStream, observers)
    }

    override def toString = s"${name.value}:Rx@${Integer.toHexString(hashCode()).take(2)}($now)"
  }

}
