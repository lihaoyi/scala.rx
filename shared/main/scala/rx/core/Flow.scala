package rx.core

import ref.WeakReference


/**
 * A member of a Scala.Rx dataflow graph. Apart from the fact that it has a name
 * and a current level in the graph, we don't really know much about it. It could
 * be either a [[Emitter]] or a [[Reactor]] or both.
 */
sealed trait Node{
  /**
   * A number giving an approximate ordering of the current [[Node]] in the
   * dataflow graph it is part of.
   *
   * [[Var]]s have it set to 0 and [[Obs]] have it set to Long.MaxValue because
   * they're always the root and leaves of the graph. For other [[Rx]]s it
   * depends on their location within the dataflow graph, and can change over
   * time if the shape of the graph is changing.
   */
  def level: Long

  /**
   * The name of this object, generally passed in as a `String` when it is
   * created.
   *
   * This can be inspected later, and is handy for debugging and logging
   * purposes.
   */
  def name: String
}

/**
 * Something that emits pings. Manages a list of WeakReferences containing
 * [[Reactor]]s which need to be pinged when an event is fired.
 */
trait Emitter[+T] extends Node{
  private[this] val childrenHolder = SpinSet[Set[WeakReference[Reactor[_]]]](Set.empty)
  /**
   * Returns the list of [[Reactor]]s which are currently bound to this [[Emitter]].
   */
  def children: Set[Reactor[_]] = {
    childrenHolder().flatMap(_.get).filter(_.parents.contains(this))
  }

  /**
   * All children, children's children, etc. recursively
   * @return
   */
  def descendants: Set[Reactor[_]] = {
    children ++ children.flatMap{
      case c: Emitter[_] => c.descendants
      case c => Set.empty[Reactor[_]]
    }
  }
  /**
   * Binds the [[Reactor]] `child` to this [[Emitter]]. Any pings by this
   * [[Emitter]] will cause `child` to react.
   */
  def linkChild[R >: T](child: Reactor[R]): Unit = {
    childrenHolder.spinSet{c =>
      val newC = c.filter(_.get != None)
      val someChild = Some(child)
      if (newC.exists(_.get == someChild)) newC
      else newC + WeakReference(child)
    }
  }

  /**
   * Manually unbinds the [[Reactor]] `child` to this [[Emitter]].
   */
  def unlinkChild(child: Reactor[_]): Unit = {
    childrenHolder.spinSet(c => c.filter(_.get != Some(child)))
  }
}

/**
 * Something that can receive pings and react to them in some way. How it reacts
 * is up to the implementation.
 */
trait Reactor[-T] extends Node{

  private[this] var _alive = true

  /**
   * Whether or not this [[Reactor]] is currently alive. Only [[Reactor]]s
   * which are alive will receive updates and propagate changes.
   */
  def alive = _alive
  /**
   * The list of [[Emitter]]s which this [[Reactor]] is currently bound to.
   *
   * Any of these [[Emitters]] emitting a ping will cause this [[Reactor]]
   * to react.
   */
  def parents: Set[Emitter[_]]

  /**
   * All parents, parent's parents, etc. recursively.
   */
  def ancestors: Set[Emitter[_]] = {
    parents ++ parents.flatMap{
      case c: Reactor[_] => c.ancestors
      case _ =>  Set.empty[Emitter[_]]
    }
  }
  /**
   * Pings this [[Reactor]] with some [[Emitter]]s, causing it to react.
   */
  def ping[P: Propagator](incoming: Set[Emitter[_]]): Set[Reactor[_]]

  /**
   * Stops this Reactor from listening for updates. [[Obs]]s would stop
   * triggering, [[Rx]]s would stop updating and propagating. In Scala-JS,
   * this is necessary to allow the Reactor to be garbage collected, while
   * in Scala-JVM this is unnecessary because of weak references.
   *
   * `.kill()` is irreversible.
   */
  def kill(): Unit = {
    _alive = false
    parents.foreach(_.unlinkChild(this))
  }
}



