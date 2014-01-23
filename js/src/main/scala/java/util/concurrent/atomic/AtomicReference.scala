package java.util.concurrent.atomic

/**
 * Created by haoyi on 1/22/14.
 */
class AtomicReference[T](var value: T) extends java.io.Serializable{
  def get() = value
  def set(newValue: T) = value = newValue
  def lazySet(newValue: T) = set(newValue)
  def compareAndSet(expect: T, newValue: T) = {
    if (expect != value) false else {
      value = newValue
      true
    }
  }
  def weakCompareAndSet(expect: T, newValue: T) = compareAndSet(expect, newValue)
  def getAndSet(newValue: T) = {
    val old = value
    value = newValue
    old
  }
}
class AtomicLong(v: Long) extends AtomicReference[Long](v){
  def getAndIncrement() = {
    value += 1
    value - 1
  }
  def getAndDecrement() = {
    value -= 1
    value + 1
  }
  def getAndAdd(delta: Long) = {
    value += delta
    value - delta
  }
  def incrementAndGet() = {
    value += 1
    value
  }
  def decrementAndGet() = {
    value -= 1
    value
  }
  def addAndGet(delta: Long) = {
    value += delta
    value
  }
  def intValue() = value.toInt
  def longValue() = value.toLong
  def floatValue() = value.toFloat
  def doubleValue() = value.toDouble
}