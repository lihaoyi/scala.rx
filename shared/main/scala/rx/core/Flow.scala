package rx.core

import ref.WeakReference


private[rx] trait Node{
  protected[rx] def level: Long

  /**
   * The name of this object, generally passed in as a `String` when it is
   * created.
   *
   * This can be inspected later, and is handy for debugging and logging
   * purposes.
   */
  def name: String
  protected[this] def debug(s: String) {
    println(name + ": " + s)
  }
}

/**
 * Something that emits pings. Manages a list of WeakReferences containing
 * [[Reactor]]s which need to be pinged when an event is fired.
 */
trait Emitter[+T] extends Node{
  private[this] val childrenHolder = SpinSet[List[WeakReference[Reactor[T]]]](Nil)
  /**
   * Returns the list of [[Reactor]]s which are currently bound to this [[Emitter]].
   */
  def children: Seq[Reactor[Nothing]] = childrenHolder.get.flatMap(_.get)

  /**
   * All children, children's children, etc. recursively
   * @return
   */
  def descendants: Seq[Reactor[Nothing]] = {
    children ++ children.flatMap{
      case c: Emitter[_] => c.descendants
      case c => Nil
    }
  }
  /**
   * Binds the [[Reactor]] `child` to this [[Emitter]]. Any pings by this
   * [[Emitter]] will cause `child` to react.
   */
  def linkChild[R >: T](child: Reactor[R]): Unit = {

    childrenHolder.spinSet{c =>
      if (c.toIterator.map(_.get).contains(Some(child))) c
      else WeakReference(child) :: c
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
  def parents: Seq[Emitter[Any]]

  /**
   * All parents, parent's parents, etc. recursively.
   */
  def ancestors: Seq[Emitter[Any]] = {
    parents ++ parents.flatMap{
      case c: Reactor[_] => c.ancestors
      case _ =>  Nil
    }
  }
  /**
   * Pings this [[Reactor]] with some [[Emitter]]s, causing it to react.
   */
  def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]]

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
    for (parent <- parents){
      parent.unlinkChild(this)
    }
  }
}



