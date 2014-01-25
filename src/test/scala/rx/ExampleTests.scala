package rx

import org.scalatest.FreeSpec
import org.scalatest.concurrent.Eventually
import Eventually._
import concurrent.duration._
import org.scalatest.exceptions.TestFailedDueToTimeoutException
import scala.concurrent.ExecutionContext.Implicits.global
import ops.Timer

/**
 * Holds code used in the doc examples, to make sure they execute correctly.
 *
 * Only running it in JVM, since a bunch of the tests don't run on the ScalaJS
 * test suite. The test coverage is good enough there shouldn't be a problem.
 */
class ExampleTests extends FreeSpec {
  implicit val patience = PatienceConfig(1 second)
  implicit val scheduler = new TestScheduler
  "ExampleTests" - {
    "obs getting GCed" in {

    }
    "should be GCed when its reference is lost" in {

    }
  }
}
