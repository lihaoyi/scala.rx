package rx.core
import acyclic.file
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A wrapper around `AtomicReference`, allowing you to apply "atomic"
 * transforms to the boxed data by using a Compare-And-Set retry loop.
 */
case class SpinSet[T](t: T) extends AtomicReference[T](t){
  def apply() = get
  def update(t: T) = set(t)
  @tailrec final def spinSet(transform: T => T): Unit = {
    val oldV = this()
    val newV = transform(oldV)
    if (!compareAndSet(oldV, newV)) {
      spinSet(transform)
    }
  }
  @tailrec final def spinSetOpt(transform: T => Option[T]): Boolean = {
    val oldV = this()
    val newVOpt = transform(oldV)
    newVOpt match{
      case Some(newV) => if (!compareAndSet(oldV, newV)) {
        spinSetOpt(transform)
      } else true
      case None => false
    }

  }
}