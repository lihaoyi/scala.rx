package rx.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import collection.mutable
import rx._

// to run this benchmark in JS:
// ;set mainClass in Compile := Some("rx.bench.RxRecalc"); run
object RxRecalc {
  def generateLatticeDataFlow(nodes: Int)(implicit ctx: Ctx.Owner): (Var[Int], Rx[Int]) = {
    // computes https://oeis.org/A162551
    // 0, 2, 8, 30, 112, 420, 1584, 6006, 22880, ...
    val n = Math.sqrt(nodes).floor.toInt
    val input = Var(0)
    var current = Array[Rx[Int]](input)
    var i = current.length + 1
    while (i <= n) {
      current = {
        Array.tabulate(i){ j =>
          if (j == 0) current(0).map(_ + 1)
          else if (j == i - 1) current.last.map(_ + 1)
          else {
            val a = current(j - 1)
            val b = current(j)
            Rx { a() + b() }
          }
        }
      }
      i += 1
    }
    i -= 2
    while (i > 0) {
      current = {
        Array.tabulate(i){ j =>
          val a = current(j)
          val b = current(j + 1)
          Rx { a() + b() }
        }
      }
      i -= 1
    }
    val output = current(0)
    (input, output)
  }

  def evaluateLatticeDataFlow(nodes: Int): Int = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val (input, output) = generateLatticeDataFlow(nodes)
    input() = 2
    input() = 0
    output.now
  }

  if (evaluateLatticeDataFlow(1 * 1) != 0) throw new Exception("invalid dataflow")
  if (evaluateLatticeDataFlow(2 * 2) != 2) throw new Exception("invalid dataflow")
  if (evaluateLatticeDataFlow(3 * 3) != 8) throw new Exception("invalid dataflow")
  if (evaluateLatticeDataFlow(4 * 4) != 30) throw new Exception("invalid dataflow")
  if (evaluateLatticeDataFlow(5 * 5) != 112) throw new Exception("invalid dataflow")

  def main(args: Array[String]): Unit = {
    assert(false, "assertions enabled")

    val comparison = Comparison("RxRecalc", Seq(
      Benchmark[Var[Int]](
        "evaluate lattice",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val (input, _) = generateLatticeDataFlow(size)
          input
        },
        {
          input =>
            input() = 1
        }
      )
    ))
    runComparison(comparison, List(10, 100, 1000, 10000), 120 seconds)

    ()
  }
}
