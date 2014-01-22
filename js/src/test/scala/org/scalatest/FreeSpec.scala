package org.scalatest
import scala.scalajs.test.JasmineTest

class FreeSpec extends JasmineTest {
  implicit class SuperString(s: String){
    def in(thunk: => Unit) = {
      it(s)(thunk)
    }
    def -(thunk: => Unit) = {
      describe(s)(thunk)
    }

  }
}
object Assertions{
  def intercept[T](thunk: => Unit) = {
    try {
      thunk
      throw new AssertionError()
    } catch {case e: T =>
      ()
    }
  }
}
object Inside{
  def inside[T](value: T)(pf: PartialFunction[T, Unit]) {
    if (!pf.isDefinedAt(value)) throw new AssertionError()
  }
}
package concurrent{
  object Eventually{
    def eventually[T](t: T): T = {
      val start = System.currentTimeMillis()
      while(System.currentTimeMillis() - start < 1000){
        return t
      }
      throw new AssertionError()
    }
  }
}