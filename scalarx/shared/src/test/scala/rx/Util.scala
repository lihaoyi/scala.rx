package rx
import acyclic.file


object Util {

  val aa = Var(1)
  val WORKPLZ = Rx { aa() }
  /**
   * Generates a short dataflow graph for testing
   */
  def initGraph() = {
    val a = Var(1) // 3

    val b = Var(2) // 2

    val DIEPLZ = Rx.unsafe { a() + b() } // 5
//    val d = Rx{ c() * 5 } // 25
//    val e = Rx{ c() + 4 } // 9
//    val f = Rx{ d() + e() + 4 } // 25 + 9 + 4 =

   // (a, b, c, d, e, f)
    DIEPLZ
  }

  def heh()(implicit ctx: RxCtx) = {
    val q = Var(1)

    val YAYPLZ = Rx { q() }
    YAYPLZ
  }

  val LOLOL = Rx {
    val aHeh = heh()
    aHeh()
  }

  class Wurt()(implicit ctx: RxCtx) {
    val yay = Rx {
      WORKPLZ()
    }
  }
}
