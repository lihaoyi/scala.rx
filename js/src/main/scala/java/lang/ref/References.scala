package java.lang.ref

abstract class Reference[T >: Null <: AnyRef](var referent: T){
  def get: T = referent
  def clear: Unit = referent = null
}
class WeakReference[T >: Null <: AnyRef](referent: T, queue: ReferenceQueue[_]) extends Reference[T](referent){
  def this(referent: T) = this(referent, null)
}
class SoftReference[T >: Null <: AnyRef](referent: T, queue: ReferenceQueue[_]) extends Reference[T](referent){
  def this(referent: T) = this(referent, null)
}
class PhantomReference[T >: Null <: AnyRef](referent: T, queue: ReferenceQueue[_]) extends Reference[T](referent){
  def this(referent: T) = this(referent, null)
}