package rx

import scala.ref.ReferenceQueue


object Main {
  def main(args: Array[String]): Unit = {
    println(1 / 0)
    throw new Exception()
//    val a = Var(1)
//    val b = Rx{ 1 / a() }
//    println(b() == 1)
//    println(b.toTry)// == Success(1))
//    a() = 0
//
//    println(b.toTry) //{ case Failure(_) => () }
  }
}

