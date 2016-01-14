package rx

import utest._
import rx.async._
import scala.concurrent.{ExecutionContext, Promise}

object AsyncTests extends TestSuite {

  implicit val executionContext = new ExecutionContext {
    def reportFailure(t: Throwable) { t.printStackTrace() }
    def execute(runnable: Runnable) {runnable.run()}
  }

  implicit val testctx = RxCtx.safe()

  def tests = TestSuite {
    "async" - {
      "basicExample" - {
        val p = Promise[Int]()
        val a = p.future.toRx(10)
        p.success(5)
        assert(a.now == 5)
      }
      "repeatedlySendingOutFutures" - {
        var p = Promise[Int]()
        val a = Var(1)

        val b: Rx[Int] = Rx {
          val f =  p.future.toRx(10)
          f() + a()
        }

        assert(b.now == 11)
        p.success(5)
        assert(b.now == 6)

        p = Promise[Int]()
        a() = 2
        assert(b.now == 12)

        p.success(7)
        assert(b.now == 9)
      }
    }
  }
}