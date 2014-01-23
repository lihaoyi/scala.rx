package java.lang.ref

abstract class Reference[T >: Null <: AnyRef](var referent: T){
  def get: T = referent
  def clear: Unit = referent = null
  override def hashCode = 1
}
class WeakReference[T >: Null <: AnyRef](referent: T) extends Reference[T](referent){
  def this(referent: T, queue: ReferenceQueue[_]) = this(referent)
}
