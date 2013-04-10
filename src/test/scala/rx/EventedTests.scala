package rx

import org.scalatest.FreeSpec
import org.scalatest.concurrent.Eventually
import concurrent.duration._
import org.scalatest.time.{Millis, Span}
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem


/**
 * Tests cases where the Rxs are able to give off events and begin propagations
 * entirely on their own. Timers, Delays, Debounces, etc.
 */
class EventedTests extends FreeSpec with Eventually{
  implicit val patience = (PatienceConfig(Span(500, Millis)))
  implicit val prop = Propagator.Immediate
  implicit val executionContext = new ExecutionContext {
    def reportFailure(t: Throwable) { t.printStackTrace() }
    def execute(runnable: Runnable) {runnable.run()}
  }
  implicit val system = ActorSystem()

  "a Timer should work properly and give off events on its own" in {

    val t = Timer(100 millis)
    var count = 0
    val o = Obs(t){
      count = count + 1
    }

    for(i <- 3 to 10){
      eventually{ assert(t() == i) }
    }

    assert(count >= 5)
  }

  "debounce" - {

    val a = Var(10)
    val b = a.debounce(200 millis)
    val c = Rx( a() * 2 ).debounce(200 millis)
    var count = 0
    val o = Obs(b){ count += 1 }
    a() = 5
    assert(b() === 5)
    assert(c() === 10)

    a() = 2
    assert(b() === 5)
    assert(c() === 10)

    a() = 7
    assert(b() === 5)
    assert(c() === 10)

    eventually{
      assert(b() === 7)
      assert(c() === 14)
    }

    a() = 1
    assert(b() === 7)
    assert(c() === 14)

    eventually{
      assert(b() === 1)
      assert(c() === 2)
    }

    assert(count === 3)


  }
  /*"delayed" in {
    val a = Var(10)
    val b = a.delayed(250 millis)
    val c = Rx( a() * 2 ).delayed(250 millis)
    var count = 0
    val ob = Obs(b){ count += 1 }
    val oa = Obs(c){ count += 1 }

    a() = 5
    assert(b() === 10)
    assert(c() === 20)
    eventually{
      assert(b() === 5)
      assert(c() === 10)
    }
    a() = 2
    assert(b() === 5)
    assert(c() === 10)
    a() = 4
    assert(b() === 5)
    assert(c() === 10)
    eventually{
      assert(b() === 4)
      assert(c() === 8)
    }
    a() = 7
    assert(b() === 4)
    assert(c() === 8)
    eventually{
      assert(b() === 7)
      assert(c() === 14)
    }
  }*/
}