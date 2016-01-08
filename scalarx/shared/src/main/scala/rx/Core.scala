package rx

import scala.annotation.compileTimeOnly
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
    def addDownstream(ctx: RxCtx) = {
      downStream.add(ctx.rx)
      ctx.rx.Internal.upStream.add(self)
      ctx.rx.Internal.depth = ctx.rx.Internal.depth max (Internal.depth + 1)
    }
  }

  def Internal: Internal

  /**
   * Get the current value of this [[Node]] and listen for updates. Only
   * callable with an `Rx{...}` block (or equivalently when an implicit
   * [[RxCtx]] is available), and the contextual/implicit [[Rx]] is the
   * one that will update when the value of this [[Node]] changes.
   */
  def apply()(implicit ctx: RxCtx) = {
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

  private [rx] def ctxKill(): Unit


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

  def now = Internal.value

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

  def kill() = {
    Internal.clearDownstream()
  }

  override def ctxKill() = kill()
}

object Rx{
  /**
   * Constructs a new [[Rx]] from an expression, that will be re-run any time
   * an upstream [[Node]] changes to re-calculate the value of this [[Rx]].
   *
   * Also injects an implicit [[RxCtx]] into that block, which serves to keep
   * track of which other [[Node]]s are used within that block (via their
   * `apply` methods) so this [[Rx]] can recalculate when upstream changes.
   */
  def apply[T](func: => T)(implicit curCtx: rx.RxCtx): Rx[T] = macro buildMacro[T]

  def buildMacro[T: c.WeakTypeTag](c: Context)(func: c.Expr[T])(curCtx: c.Expr[rx.RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._

    val ctxName =  c.fresh[TermName]("rxctx")

    object transformer extends c.universe.Transformer {
      override def transform(tree: c.Tree): c.Tree = {
        if (c.weakTypeOf[RxCtx.Dummy.type] == tree.tpe) q"$ctxName"
        else if(curCtx.tree.toString() == tree.toString()) q"$ctxName"
        else super.transform(tree)
      }
    }
    val res = q"rx.Rx.build{$ctxName: rx.RxCtx => ${transformer.transform(func.tree)}}($curCtx)"

    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  /**
   * Constructs a new [[Rx]] from an expression (which explicitly takes an
   * [[RxCtx]]) and an optional `owner` [[RxCtx]].
   */
  def build[T](func: RxCtx => T)(implicit owner: RxCtx): Rx[T] = {
    new Rx(func, if(owner == RxCtx.Dummy) None else Some(owner))
  }
}

/**
 * A [[Node]] that depends on other [[Node]]s, updating automatically
 * when their value changes. Optionally has an [[owner]], which is
 * another [[Rx]] this one was defined within. The [[Rx]] gets killed
 * automatically when the [[owner]] recalculates, in order to avoid
 * memory leaks from un-used [[Rx]]s hanging around.
 */
class Rx[+T](func: RxCtx => T, owner: Option[RxCtx]) extends Node[T] { self =>

  owner.foreach { o =>
    o.rx.Internal.owned.add(self)
    o.rx.Internal.addDownstream(new RxCtx(self))
  }

  object Internal extends Internal{

    def owner = self.owner

    private [this] var cached: Try[T] = scala.util.Failure(new Throwable)

    var depth = 0
    var dead = false
    val upStream = mutable.Set.empty[Node[_]]
    val owned = mutable.Set.empty[Node[_]]

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
      Internal.owned.foreach(_.ctxKill())
      Internal.owned.clear()
      Try(func(new RxCtx(self)))
    }

    def update() = {
      cached = calc()
    }

    def current(): Try[T] = cached
  }

  Internal.update()

  def now = Internal.current().get

  /**
   * @return the current value of this [[Rx]] as a `Try`
   */
  def toTry = Internal.current()

  def ctxKill(): Unit = {
    Internal.dead = true
    Internal.clearDownstream()
    Internal.clearUpstream()
  }

  def kill(): Unit = {
    owner.foreach(_.rx.Internal.owned.remove(this))
    ctxKill()
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
object RxCtx{
  implicit object Dummy extends RxCtx(throw new Exception(
    "Invalid RxCtx: you can only call `Rx.apply` within an " +
    "`Rx{...}` block or where an implicit `RxCtx` is available"
  ))

//  @compileTimeOnly("Todo Msg RxCtx Bad")
//  implicit def dummy(): RxCtx = Dummy
}

/**
 * An implicit scope representing a "currently evaluating" [[Rx]]. Used to keep
 * track of dependencies or ownership.
 */
class RxCtx(rx0: => Rx[_]){
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