package rx

import scala.concurrent.{ExecutionContext, Future, Promise}
import rx.core.Propagator
import ops._

import utest._
import acyclic.file
/**
 * Tests combinators with asynchronous behavior. All tests are run using a
 * run-immediately-on-this-thread execution context, to remove any
 * multi-threadedness and keep the tests deterministic
 */
object AsyncTests extends TestSuite{

  implicit val prop = Propagator.Immediate
  implicit val executionContext = new ExecutionContext {
    def reportFailure(t: Throwable) { t.printStackTrace() }
    def execute(runnable: Runnable) {runnable.run()}
  }
  def tests = TestSuite{

    "async" - {
      "basicExample" - {
        val p = Promise[Int]()
        val a = Rx{
          p.future
        }.async(10)
        assert(a() == 10)

        p.success(5)
        assert(a() == 5)
      }
      "repeatedlySendingOutFutures" - {
        var p = Promise[Int]()
        val a = Var(1)
        val b = Rx{
          val A = a()
          p.future.map{_ + A}
        }.async(10)
        assert(b() == 10)

        p.success(5)
        assert(b() == 6)

        p = Promise[Int]()
        a() = 2
        assert(b() == 6)

        p.success(7)
        assert(b() == 9)
      }
      "propagationShouldContinueAfterAsyncRx" - {
        var p = Promise[Int]()
        val a = Var(1)
        val b = Rx{
          val A = a()
          p.future.map{x => x + A}
        }.async(10)
        val c = Rx{ b() + 1 }
        assert(c() == 11)

        p.success(5)
        assert(c() == 7)

        p = Promise[Int]()
        a() = 2
        assert(c() == 7)

        p.success(7)
        assert(c() == 10)

      }
      "futuresThatGetCompletedOutOfOrderAreReceivedOutOfOrder" - {
        val p = Seq.fill(3)(Promise[Int]())
        val a = Var(0)
        val b = Rx{ p(a()).future }.async(10, discardLate=false)

        assert(b() == 10)

        a() = 1
        a() = 2

        p(2).success(2)

        assert(b() == 2)

        p(1).success(1)
        assert(b() == 1)

        p(0).success(0)
        assert(b() == 0)
      }
      "droppingTheResultOfFuturesWhichReturnOutOfOrder" - {
        val p = Seq.fill(3)(Promise[Int]())
        val a = Var(0)
        val b = Rx{ p(a()).future }.async(10)

        assert(b() == 10)

        a() = 1
        a() = 2

        p(2).success(2)
        assert(b() == 2)

        p(1).success(1)
        assert(b() == 2)

        p(0).success(0)
        assert(b() == 2)
      }

      "eventsEmergeFromAsyncDynamic" - {
        val a = Var(0)
        val b = Rx{ Future.successful(10 + a()) }.async(10)
        var count = 0
        val o = Obs(b){ count += 1 }
        assert(count == 1)
        a() = 10

        assert(count == 2)

      }
    }
  }

}