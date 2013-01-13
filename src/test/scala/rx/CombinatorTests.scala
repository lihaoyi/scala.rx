package rx

import org.scalatest._
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import Combinators._
class CombinatorTests extends FreeSpec{
  "skipFailure" in {
    val x = Var(10)
    val y = Sig{ 100 / x() }.skipFailures
    val z = Sig{ y() + 20 }
    assert(y() === 10)
    assert(z() === 30)
    x() = 0
    assert(y() === 10)
    assert(z() === 30)
    x() = 5
    assert(y() === 20)
    assert(z() === 40)
  }

  "filter/skips" - {
    "default" in {
      val a = Var(10)
      var b = 8
      val c = Sig{ a() + b }
      var count = 0
      val o = Obs(c.filter()){ count += 1 }
      b = 10
      a() = 8
      assert(count === 0)
    }
    "filter" in test[Int](_.filter(_%2 != _%2))
    "skip" in test[Int](_.skip(_%2 == _%2))
    "filterTry" in test[Int](_.filterTry(_.map(_%2) != _.map(_%2)))
    "skipTry" in test[Int](_.skipTry(_.map(_%2) == _.map(_%2)))
    def test[T](op: Signal[T] => Signal[T]) = {

      val a = Var{10}
      val b = a.filter{_ % 2 != _ % 2}
      val c = Sig{ b() }
      val d = Sig{ a() }.filter(_ % 3 != _ % 3)
      a() = 12
      assert(c() === 10)
      assert(d() === 12)
      a() = 13
      assert(c() === 13)
      assert(d() === 13)
      a() = 15
      assert(c() === 13)
      assert(d() === 15)
      a() = 18
      assert(c() === 18)
      assert(d() === 15)

    }
  }
}