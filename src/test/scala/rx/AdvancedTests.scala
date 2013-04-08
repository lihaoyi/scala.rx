package rx

import org.scalatest._
import concurrent.Eventually
import scala.concurrent.{ExecutionContext, Await, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.ActorSystem

import time.{Millis, Span}


class AdvancedTests extends FreeSpec with Eventually{
  implicit val patience = (PatienceConfig(Span(500, Millis)))
  implicit val system = ActorSystem()

  "disabling" - {
    "sigs" in {
      val a = Var(1)
      val b = Rx{ 2 * a() }
      assert(b() === 2)
      a() = 2
      assert(b() === 4)
      b.active = false
      a() = 10
      assert(b() === 4)
    }
    "obs" in {
      val a = Var(1)
      val b = Rx{ 2 * a() }
      var target = 0
      val o = Obs(b){
        target = b()
      }
      assert(target === 0)
      a() = 2
      assert(target === 4)
      o.active = false
      a() = 3
      assert(target === 4)
    }
  }
  "async" - {
    "basic example" in {
      val p = Promise[Int]()
      val a = Rx{
        p.future
      }.async(10)
      assert(a() === 10)
      p.complete(scala.util.Success(5))
      eventually {
        assert(a() === 5)
      }
    }
    "repeatedly sending out Futures" in {
      var p = Promise[Int]()
      val a = Var(1)
      val b = Rx{
        val A = a()
        p.future.map{_ + A}
      }.async(10)
      assert(b() === 10)
      p.complete(scala.util.Success(5))
      eventually{
        assert(b() === 6)
      }
      p = Promise[Int]()
      a() = 2
      assert(b() === 6)
      p.complete(scala.util.Success(7))
      eventually{
        assert(b() === 9)
      }
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
      p.complete(scala.util.Success(5))
      eventually{
        assert(c() === 7)
      }
      p = Promise[Int]()
      a() = 2
      assert(c() === 7)
      p.complete(scala.util.Success(7))
      eventually{
        assert(c() === 10)
      }
    }
    "ensuring that sent futures that get completed out of order are received out of order" in {
      var p = Seq[Promise[Int]](Promise(), Promise(), Promise())
      val a = Var(0)
      val b = Rx{ p(a()).future }.async(10)

      assert(b() === 10)

      a() = 1
      a() = 2

      p(2).complete(scala.util.Success(2))
      eventually{
        assert(b() === 2)
      }
      p(1).complete(scala.util.Success(1))
      eventually{
        assert(b() === 1)
      }
      p(0).complete(scala.util.Success(0))
      eventually{
        assert(b() === 0)
      }
    }
    "dropping the result of Futures which return out of order" in {
      var p = Seq[Promise[Int]](Promise(), Promise(), Promise())
      val a = Var(0)
      val b = Rx{ p(a()).future }.async(10, AsyncSignals.DiscardLate())

      assert(b() === 10)

      a() = 1
      a() = 2

      p(2).complete(scala.util.Success(2))
      eventually{
        assert(b() === 2)
      }
      p(1).complete(scala.util.Success(1))
      eventually{
        assert(b() === 2)
      }
      p(0).complete(scala.util.Success(0))
      eventually{
        assert(b() === 2)
      }

    }

    "ensuring that events emerge from the .async DynamicRxnal" in {
      val a = Var(0)
      val b = Rx{ Future.successful(10 + a()) }.async(10)
      var count = 0
      val o = Obs(b){ count += 1 }
      a() = 10

      eventually{
        assert(count == 1)
      }

    }
  }
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

  "swapping in a parallelizing Propagator should speed things up significantly" in {

    def time[P](implicit p: Propagator[P], post: P => Unit = (x: P) => ()) = {
      def spinner(a: Flow.Signal[Int]) = Rx{
        var count = 0
        for(x <- 0 until 200000000){
          count += 1
        }
        count + a()
      }
      val a = Var(0)
      val b = spinner(a)
      val c = spinner(a)
      val d = spinner(a)
      val start = System.currentTimeMillis()
      post(a() = 10)
      val end = System.currentTimeMillis()
      (b(), c(), d(), (end - start))
    }


    val serialResult = time[Unit](Propagator.Immediate)
    val parallelResult = time[Future[Unit]](new Propagator.Parallelizing()(ExecutionContext.global), Await.result(_, 10 seconds))

    // serial and parallel should have the same result but parallel
    // should be at least 1.5 times as fast
    (serialResult, parallelResult) patternMatches {
      case ((200000010, 200000010, 200000010, serialTime),
            (200000010, 200000010, 200000010, parallelTime))
        if serialTime * 1.0 / parallelTime > 1.5 =>
    }
  }

  /*
  "recursion" - {
    "calculating fixed point" in {
      lazy val s: Rx[Double] = Rx{ Math.cos(s()) }
      println(s())
    }
    "calculating sqrt" in {
      lazy val s: Rx[Double] = Rx(default = 10.0){ s() - (s() * s() - 10) / (2 * s()) }
      println(s())
    }
  }
   */
  implicit class MatchPimp[T](value: T){
    def patternMatches(f: PartialFunction[T, Any]) = {
      assert(
        f.isDefinedAt(value),
        s"patternMatch failed: $value does not match pattern"
      )
      f(value)
    }
  }

}