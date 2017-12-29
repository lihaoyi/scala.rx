package rx

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import utest._
import concurrent.duration._
import rx.async._

object EventedTests extends TestSuite {

  implicit val todo = new AsyncScheduler(Executors.newSingleThreadScheduledExecutor(),ExecutionContext.Implicits.global)
  import Ctx.Owner.Unsafe._


  def tests = utest.Tests {
//    "debounce" - {
//      "simple" - {
//        val a = Var(10)
//        val b = a.debounce(1000.millis)
//        a() = 5
//        assert(b.now == 10)
//
//        eventually {
//          b.now == 5
//        }
//        val curr = b.now
//        a() = 2
//        assert(b.now == curr)
//
//        eventually {
//          b.now == 2
//        }
//
//        a() = 1
//        a() = 5
//        a() = 42
//
//        assert(b.now == 2)
//
//        eventually {
//          b.now == 42
//        }
//      }
//      "longer" - {
//        val a = Var(10)
//        val b = a.debounce(1000.millis)
//        val c = Rx( a() * 2 ).debounce(1000.millis)
//        var count = 0
//        val o = b.triggerLater { count += 1 }
//        a() = 5
//        eventually(
//          b.now == 5,
//          c.now == 10
//        )
//        a() = 2
//        assert(
//          b.now == 5,
//          c.now == 10
//        )
//
//        a() = 7
//        assert(
//          b.now == 5,
//          c.now == 10
//        )
//
//        eventually(
//          b.now == 7,
//          c.now == 14
//        )
//
//        a() = 1
//        assert(
//          b.now == 7,
//          c.now == 14
//        )
//
//        eventually(
//          b.now == 1,
//          c.now == 2
//        )
//
//        a() = 3
//        a() = 4
//        a() = 42
//
//        eventually(
//          b.now == 42,
//          c.now == 84
//        )
//
//        assert(count == 4)
//      }
//    }
//    "delayed" - {
//      "simple" - {
//        val a = Var(10)
//        val b = a.delay(1000.millis)
//        a() = 5
//        assert(b.now == 10)
//        eventually(
//          b.now == 5
//        )
//        a() = 4
//        a() = 5
//        a() = 6
//        assert(b.now == 4)
//        eventually(
//          b.now == 5
//        )
//        eventually(
//          b.now == 6
//        )
//      }
//      "longer" - {
//        val a = Var(10)
//        val b = a.delay(1000 millis)
//        val c = Rx( a() * 2 ).delay(1000 millis)
//        var count = 0
//        c.trigger(count += 1)
//        a() = 5
//        assert(
//          b.now == 10,
//          c.now == 20
//        )
//        eventually(
//          b.now == 5,
//          c.now == 10
//        )
//
//        a() = 4
//        assert(
//          b.now == 4,
//          c.now == 8
//        )
//        a() = 7
//        assert(
//          b.now == 4,
//          c.now == 8
//        )
//        eventually(
//          b.now == 7,
//          c.now == 14
//        )
//
//        assert(count == 4)
//      }
//    }
//    "timer" - {
//      var count = 0
//      val timer = Timer(100.milli)
//      timer.foreach(i => count += 1)
//      eventually(count == 2)
//      eventually(count == 4)
//      timer.kill()
//      intercept[AssertionError] {
//        eventually(count == 6)
//      }
//    }
  }
}
