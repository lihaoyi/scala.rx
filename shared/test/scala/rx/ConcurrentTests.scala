package rx

import org.scalatest._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import java.util.concurrent.CountDownLatch

/**
 * Tests that force Rxs to run in parallel (whether the same Rx or different Rxs)
 * to verify their behavior in such situations.
 */
class ConcurrentTests extends FreeSpec {

  implicit val system = new TestScheduler()
  implicit class awaitable[T](f: Future[T]){
    def await(x: Duration = 10.seconds) = Await.result(f, x)
  }
  implicit val prop = Propagator.Immediate
  "parallel execution of a single Rx" - {
    def setup = {
      val ps = Seq.fill(3)(new CountDownLatch(1))
      val wall = Seq.fill(3)(new CountDownLatch(1))
      ps(0).countDown()
      var i = 0
      val a = Var(0)
      val b = Rx{
        val j = i
        i+= 1
        val av = a()
        wall(j).countDown()
        ps(j).await()
        av
      }
      (ps, a, b, wall)
    }

    /**
     * This ensures that if I have two parallel runs which overlap as follows:
     *
     * A:     |--------------------------------->|
     * B:                    |----------->|
     *
     * Res: -----------------?------------>|--------B------------>
     *
     * The result for B will be kept and A will be discarded. The mess of
     * promises is used as semaphores to force the two executions to overlap
     * exactly as shown above
     */
    "Dynamic with full parallel overlap" in {
      val (ps, a, b, wall) = setup

      val set1 = Future{a() = 1}
      wall(1).await()

      val set2 = Future{a() = 2}
      wall(2).await()

      ps(2).countDown()
      ps(1).countDown()

      set1.await()
      assert(b() == 2)
      set2.await()
      assert(b() == 2)
    }

    /**
     * This ensures that if two parallel runs which overlap as follows:
     *
     * A:     |------------------------>|
     * B:                    |--------------------->|
     *
     * Res: -----------?--------------->|-----A---->|------B------->
     * Both the results for A and B will be available
     */
    "Dynamic with partial parallel overlap" in {
      val (ps, a, b, wall) = setup

      val set1 = Future{a() = 1}
      wall(1).await()

      val set2 = Future{a() = 2}
      wall(2).await()

      ps(1).countDown()
      set1.await()
      assert(b() == 1)
      ps(2).countDown()

      set2.await()
      assert(b() == 2)
    }
  }
}