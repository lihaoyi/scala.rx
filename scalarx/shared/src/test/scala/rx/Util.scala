package rx
import acyclic.file

object Util {
  /**
   * Generates a short dataflow graph for testing
   */
  def initGraph()(implicit ctx: RxCtx) = {
    val a = Var(1) // 3

    val b = Var(2) // 2

    val c = Rx{ a() + b() } // 5
    val d = Rx{ c() * 5 } // 25
    val e = Rx{ c() + 4 } // 9
    val f = Rx{ d() + e() + 4 } // 25 + 9 + 4 =

   (a, b, c, d, e, f)
  }
}