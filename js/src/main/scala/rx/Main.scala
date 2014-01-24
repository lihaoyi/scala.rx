package rx

import scala.ref.ReferenceQueue


object Main {
  def main(args: Array[String]): Unit = {
    val a = Var(0)
    val b = Rx(a() + 1)
    a() = 2
    println(b())
  }
}

