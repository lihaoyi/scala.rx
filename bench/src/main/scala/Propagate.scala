package rx.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import collection.mutable
import rx._

// to run this benchmark in JS:
// ;set mainClass in Compile := Some("rx.bench.Propagate"); run
object Propagate {

  def main(args: Array[String]): Unit = {
    assert(false, "assertions enabled")

    val comparison = Comparison("Propagate", Seq(
      Benchmark[Var[Int]](
        "update with map subcribtions",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            val endpoint = v.map(_ + i)
            endpoint.map{ _ + 1 } // subscriptions
            rxs += endpoint
            i += 1
          }
          v
        },
        { input =>
          input() = 7
        }
      ),
      Benchmark[Var[Int]](
        "update with Rx subcribtions",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            val endpoint = v.map(_ + i)
            Rx { endpoint() + 1 } // subscriptions
            rxs += endpoint
            i += 1
          }
          v
        },
        { input =>
          input() = 7
        }
      ),
      Benchmark[Var[Int]](
        "update with observers",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            val endpoint = v.map(_ + i)
            endpoint.foreach{ _ => () } // observer
            rxs += endpoint
            i += 1
          }
          v
        },
        { input =>
          input() = 7
        }
      ),
      Benchmark[Var[Int]](
        "update without subscription",
        { size =>
          implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
          val v = Var(0)
          var i = 0
          val rxs = mutable.ArrayBuffer.empty[Rx[Int]]
          while (i < size) {
            val endpoint = v.map(_ + i)
            rxs += endpoint
            i += 1
          }
          v
        },
        { input =>
          input() = 7
        }
      )
    ))
    runComparison(comparison, List(10, 100, 1000), 120 seconds)

    ()
  }
}
