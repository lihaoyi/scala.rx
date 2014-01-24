package rx

import org.scalatest.{Assertions, FreeSpec}
import org.scalatest.concurrent.Eventually
import Eventually._
import concurrent.duration._
import org.scalatest.exceptions.TestFailedDueToTimeoutException
import Assertions._
import scala.concurrent.ExecutionContext.Implicits.global
class GcTests extends FreeSpec {
  implicit val patience = PatienceConfig(1 second)
  implicit val scheduler = new TestScheduler
  "GcTests" - {
    "obs getting GCed" in {
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
        assert(oldCount == count)
      }(patience)
    }
    "should be GCed when its reference is lost" in {
      var count = 0
      Timer(100 millis).foreach{ x =>
        count = count + 1
      }

      eventually{
        assert(count == 3)
      }(patience)

      System.gc

      intercept[TestFailedDueToTimeoutException]{
        eventually{
          assert(count == 4)
        }(patience)
      }
    }
  }
}
