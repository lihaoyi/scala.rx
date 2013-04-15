package rx

import org.scalatest._
import concurrent.Eventually
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._


/**
 * Tests combinators with asynchronous behavior. All tests are run using a
 * run-immediately-on-this-thread execution context, to remove any
 * multi-threadedness and keep the tests deterministic
 */
class AsyncTests extends FreeSpec{

  implicit val prop = Propagator.Immediate
  implicit val executionContext = new ExecutionContext {
    def reportFailure(t: Throwable) { t.printStackTrace() }
    def execute(runnable: Runnable) {runnable.run()}
  }

  "disabling obs" in {

    val a = Var(1)
    val b = Rx{ 2 * a() }
    var target = 0
    val o = Obs(b){
      target = b()
    }
    assert(target === 2)
    a() = 2
    assert(target === 4)
    o.active = false
    a() = 3
    assert(target === 4)

  }
  "async" - {
    "basic example" in {
      val p = Promise[Int]()
      val a = Rx{
        p.future
      }.async(10)
      assert(a() === 10)

      p.success(5)
      assert(a() === 5)
    }
    "repeatedly sending out Futures" in {
      var p = Promise[Int]()
      val a = Var(1)
      val b = Rx{
        val A = a()
        p.future.map{_ + A}
      }.async(10)
      assert(b() === 10)

      p.success(5)
      assert(b() === 6)

      p = Promise[Int]()
      a() = 2
      assert(b() === 6)

      p.success(7)
      assert(b() === 9)
    }
    "the propagation should continue after the AsyncRx" in {
      var p = Promise[Int]()
      val a = Var(1)
      val b = Rx{
        val A = a()
        p.future.map{x => x + A}
      }.async(10)
      val c = Rx{ b() + 1 }
      assert(c() === 11)

      p.success(5)
      assert(c() === 7)

      p = Promise[Int]()
      a() = 2
      assert(c() === 7)

      p.success(7)
      assert(c() === 10)

    }
    "ensuring that sent futures that get completed out of order are received out of order" in {
      var p = Seq[Promise[Int]](Promise(), Promise(), Promise())
      val a = Var(0)
      val b = Rx{ p(a()).future }.async(10, false)

      assert(b() === 10)

      a() = 1
      a() = 2

      p(2).success(2)

      assert(b() === 2)

      p(1).success(1)
      assert(b() === 1)

      p(0).success(0)
      assert(b() === 0)
    }
    "dropping the result of Futures which return out of order" in {
      var p = Seq[Promise[Int]](Promise(), Promise(), Promise())
      val a = Var(0)
      val b = Rx{ p(a()).future }.async(10, true)

      assert(b() === 10)

      a() = 1
      a() = 2

      p(2).success(2)
      assert(b() === 2)

      p(1).success(1)
      assert(b() === 2)

      p(0).success(0)
      assert(b() === 2)
    }

    "ensuring that events emerge from the .async Dynamic" in {
      val a = Var(0)
      val b = Rx{ Future.successful(10 + a()) }.async(10)
      var count = 0
      val o = Obs(b){ count += 1 }
      assert(count === 1)
      a() = 10

      assert(count === 2)

    }
  }










}