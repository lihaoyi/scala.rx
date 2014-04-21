package rx

import concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import ops.Timer
import utest._
/**
 * Checking that weak references are doing their job on the JVM by forcing GCs.
 */
object GcTests extends TestSuite{
  implicit val scheduler = new TestScheduler
  def tests = TestSuite {
    "obsGetGCed" - {
      val a = Var(1)
      var count = 0
      Obs(a){
        count = count + 1
      }
      assert(count == 1)
      eventually{
        val oldCount = count
        a() = a() + 1
        System.gc()
        oldCount == count
      }
    }
    "timersGetGced" - {
      var count = 0
      Timer(100 millis).foreach{ x =>
        count = count + 1
      }

      eventually{
        count == 3
      }

      System.gc()

      intercept[AssertionError]{
        eventually{
          count == 4
        }
      }
    }
  }
}
