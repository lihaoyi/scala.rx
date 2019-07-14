package rx.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import collection.mutable
import rx._

// to run this benchmark in JS:
// ;set mainClass in Compile := Some("rx.bench.Subscribe"); run
object Subscribe {

  def main(args: Array[String]): Unit = {
    assert(false, "assertions enabled")

    val comparison = Comparison("Subscribe", Seq(
      BenchmarkWithoutInit(
        "via map",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            rxs += v.map(_ + i)
            i += 1
          }
        }
      ),
      BenchmarkWithoutInit(
        "via Rx",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            rxs += Rx { v() + i }
            i += 1
          }
        }
      ),
      BenchmarkWithoutInit(
        "via Observer",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            v.foreach{ _ => () }
            rxs += v
            i += 1
          }
        }
      )
    ))
    runComparison(comparison, List(10, 100, 1000, 10000), 120 seconds)

    ()
  }
}
