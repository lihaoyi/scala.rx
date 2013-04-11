package rx

import scala.util.{DynamicVariable, Try, Failure, Success}


import ref.WeakReference

private[rx] trait Node{
  protected[rx] def level: Long

  /**
   * The name of this object, generally passed in as a `String` when it is
   * created. This can be inspected later.
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
  private[this] val children = Atomic[List[WeakReference[Reactor[T]]]](Nil)
  /**
   * Returns the list of [[Reactor]]s which are currently bound to this [[Emitter]].
   */
  def getChildren: Seq[Reactor[Nothing]] = children.get.flatMap(_.get)

  /**
   * Binds the [[Reactor]] `child` to this [[Emitter]]. Any pings by this
   * [[Emitter]] will cause `child` to react.
   */
  def linkChild[R >: T](child: Reactor[R]): Unit = {
    children.spinSet(c => (WeakReference(child) :: c.filter(_.get.isDefined)).distinct)
  }
}

/**
 * Something that can receive pings and react to them in some way. How it reacts
 * is up to the implementation.
 */
trait Reactor[-T] extends Node{

  /**
   * Returns the list of [[Emitter]]s which this [[Reactor]] is currently bound to.
   */
  def getParents: Seq[Emitter[Any]]

  /**
   * Pings this [[Reactor]] with some [[Emitter]]s, causing it to react.
   */
  def ping[P: Propagator](incoming: Seq[Emitter[Any]]): Seq[Reactor[Nothing]]

}



