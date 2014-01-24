package rx

import scala.ref.ReferenceQueue


object Main {
  def main(args: Array[String]): Unit = {
    val a = Var(1)
    val b = Rx{ 2 * a() }
    var target = 0
    val o = Obs(b){
      target = b()
    }
    println(target == 2)
    a() = 2
    println(target == 4)
    o.kill()
    a() = 3
    println(target == 4)
  }
}

