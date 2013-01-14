package rx

import org.scalatest._
import concurrent.Eventually

import util.{Failure, Success}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import Combinators._
import scala.concurrent.ExecutionContext.Implicits.global


class AdvancedTests extends FreeSpec with Eventually{
  "disabling" - {
    "sigs" in {
      val a = Var(1)
      val b = Sig{ 2 * a() }
      assert(b() === 2)
      a() = 2
      assert(b() === 4)
      b.active = false
      a() = 10
      assert(b() === 4)
    }
    "obs" in {
      val a = Var(1)
      val b = Sig{ 2 * a() }
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
    def pause = Thread.sleep(100)
    "basic example" in {
      val p = Promise[Int]()
      val a = Sig{
        p.future
      }.async(10)
      assert(a() === 10)
      p.complete(scala.util.Success(5))
      pause
      assert(a() === 5)

    }
    "repeatedly sending out Futures" in {
      var p = Promise[Int]()
      val a = Var(1)
      val b = Sig{
        val A = a()
        p.future.map{_ + A}
      }.async(10)
      assert(b() === 10)
      p.complete(scala.util.Success(5))
      pause
      assert(b() === 6)
      p = Promise[Int]()
      a() = 2
      assert(b() === 6)
      p.complete(scala.util.Success(7))
      pause
      assert(b() === 9)
    }
    "the propagation should continue after the AsyncSig" in {
      var p = Promise[Int]()
      val a = Var(1)
      val b = Sig{
        val A = a()
        p.future.map{x => println(x + 1); x + A}
      }.async(10)
      val c = Sig{ b() + 1 }
      assert(c() === 11)
      p.complete(scala.util.Success(5))
      pause
      assert(c() === 7)
      p = Promise[Int]()
      a() = 2
      assert(c() === 7)
      p.complete(scala.util.Success(7))
      pause
      assert(c() === 10)
    }
    "ensuring that sent futures that get completed out of order are received out of order" in {
      var p = Seq[Promise[Int]](Promise(), Promise(), Promise())
      val a = Var(0)
      val b = Sig{ p(a()).future }.async(10)

      assert(b() === 10)

      a() = 1
      a() = 2

      p(2).complete(scala.util.Success(2))
      pause
      assert(b() === 2)
      p(1).complete(scala.util.Success(1))
      pause
      assert(b() === 1)
      p(0).complete(scala.util.Success(0))
      pause
      assert(b() === 0)
    }
    "dropping the result of Futures which return out of order" in {
      var p = Seq[Promise[Int]](Promise(), Promise(), Promise())
      val a = Var(0)
      val b = Sig{ p(a()).future }.async(10, _.DiscardLate.apply)

      assert(b() === 10)

      a() = 1
      a() = 2

      p(2).complete(scala.util.Success(2))
      pause
      assert(b() === 2)
      p(1).complete(scala.util.Success(1))
      pause
      assert(b() === 2)
      p(0).complete(scala.util.Success(0))
      pause
      assert(b() === 2)
    }

    "ensuring apply does the right thing" in {
      val a = Var(0)
      val b = Sig{ Future.successful(10 + a()) }.async(10)
      var count = 0
      val o = Obs(b){ println("FIRE"); count += 1 }
      a() = 10

      eventually{
        println(count)
        assert(count == 1)
      }

    }
  }

}