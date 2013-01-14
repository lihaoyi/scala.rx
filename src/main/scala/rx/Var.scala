package rx

import util.{Try, Success}
import java.util.concurrent.atomic.AtomicReference


/**
 * Contains a future that can be updated manually, triggering his children.
 */
object Var {

  def apply[T](value: T)
              (implicit name: String = "") = {
    new Var(name, value)
  }
}

/**
 * A Var is a Signal which can be changed manually via assignment. Setting
 * the future is thread-safe as the semantics are controlled by the `ready`
 * AtomicBoolean.
 *
 * @param initValue The initial future of the Var
 * @tparam T The type of the future this Var contains
 */
case class Var[T](name: String, initValue: T)
extends Signal[T]{
  val currentValueHolder = new AtomicReference[Try[T]](Success(initValue))
  def level = 0L

  def currentValue = toTry.get
  def update(newValue: Try[T]): Unit = {

    currentValueHolder.set(newValue)
    propagate(this.getChildren.map(this -> _))
  }

  def update(newValue: T): Unit = {
    currentValueHolder.set(Success(newValue))
    propagate(this.getChildren.map(this -> _))
  }

  def toTry = currentValueHolder.get
  def update(calc: T => T): Unit = {

    val oldValue = currentValue
    val newValue = calc(oldValue)
    if(!currentValueHolder.compareAndSet(Success(oldValue), Success(newValue))) update(calc)
    propagate(this.getChildren.map(this -> _))
  }
}