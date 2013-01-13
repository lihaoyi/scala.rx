package rx

import util.Try
import collection.mutable

object Call{
  trait Reactor[-T]{
    def update(msg: T): Unit
    def update(msg: Try[T]): Unit
  }

  trait Emitter[+T]{
    private[this] val children: mutable.WeakHashMap[Reactor[T], Unit] = new mutable.WeakHashMap()

    def pingChildren(): Unit

    def linkChild[R >: T](child: Reactor[R]) = children(child) = ()
  }
}