package rx

import concurrent.duration._
import scala.concurrent.ExecutionContext
import utest._
import rx.core.Propagator
import rx.ops._

/**
 * Tests cases where the Rxs are able to give off events and begin propagations
 * entirely on their own. Timers, Delays, Debounces, etc.
 */
object EventedTests extends TestSuite{
  implicit val prop = Propagator.Immediate
  implicit val executionContext = new ExecutionContext {
    def reportFailure(t: Throwable) { t.printStackTrace() }
    def execute(runnable: Runnable) {runnable.run()}
  }
  implicit val scheduler = new TestScheduler()
  def tests = TestSuite{

    "timerShouldEmitEvents" - {
      val t = Timer(100 millis)
      var count = 0
      val o = Obs(t){
        count = count + 1
      }

      for(i <- 3 to 5){
        eventually(t() == i)
      }

      assert(count >= 5)
    }


    "debounce" - {
      "simple" - {
        val a = Var(10)
        val b = a.debounce(100 millis)
        a() = 5
        assert(b() == 5)

        a() = 2
        assert(b() == 5)

        eventually{
          b() == 2
        }

        a() = 1
        assert(b() == 2)

        eventually{
          b() == 1
        }
      }
      "longer" - {
        val a = Var(10)
        val b = a.debounce(200 millis)
        val c = Rx( a() * 2 ).debounce(200 millis)
        var count = 0
        val o = Obs(b){ count += 1 }
        a() = 5
        assert(
          b() == 5,
          c() == 10
        )

        a() = 2
        assert(
          b() == 5,
          c() == 10
        )

        a() = 7
        assert(
          b() == 5,
          c() == 10
        )

        eventually(
          b() == 7,
          c() == 14
        )

        a() = 1
        assert(
          b() == 7,
          c() == 14
        )

        eventually(
          b() == 1,
          c() == 2
        )

        assert(count == 4)
      }


    }
    "delayed" - {
      "simple" - {
        val a = Var(10)
        val b = a.delay(100 millis)

        a() = 5
        assert(b() == 10)
        eventually(
          b() == 5
        )

        a() = 4
        assert(b() == 5)
        eventually(
          b() == 4
        )
      }
      "longer" - {
        val a = Var(10)
        val b = a.delay(100 millis)
        val c = Rx( a() * 2 ).delay(100 millis)
        var count = 0

        a() = 5
        assert(
          b() == 10,
          c() == 20
        )
        eventually(
          b() == 5,
          c() == 10
        )

        a() = 4
        assert(
          b() == 5,
          c() == 10
        )
        eventually(
          b() == 4,
          c() == 8
        )

        a() = 7
        assert(
          b() == 4,
          c() == 8
        )
        eventually(
          b() == 7,
          c() == 14
        )
      }
    }
  }
}