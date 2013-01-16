package rx

import org.scalatest._
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.Eventually
import akka.actor.ActorSystem

class CombinatorTests extends FreeSpec with Eventually{
  implicit val system = ActorSystem()
  "skipFailure" in {
    val x = Var(10)
    val y = Rx{ 100 / x() }.skipFailures
    val z = Rx{ y() + 20 }
    assert(y() === 10)
    assert(z() === 30)
    x() = 0
    assert(y() === 10)
    assert(z() === 30)
    x() = 5
    assert(y() === 20)
    assert(z() === 40)
  }

  "filterDiff/skipDiff" - {
    "default" in {
      val a = Var(10)
      var b = 8
      val c = Rx{ a() + b }
      var count = 0
      val o = Obs(c.filterDiff()){ count += 1 }
      b = 10
      a() = 8
      assert(count === 0)
    }
    "filterDiff" in test(x => _.filterDiff(_%x != _%x))
    "filterTry" in test(x => _.filterTry(_.map(_%x) != _.map(_%x)))
    def test(op: Int => Rx[Int] => Rx[Int]) = {

      val a = Var{10}
      val b = op(2)(a)
      val c = Rx{ b() }
      val d = op(3)(Rx{ a() })
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
  "filter" in {
    val a = Var(10)
    val b = a.filter(_ > 5)
    a() = 1
    assert(b() === 10)
    a() = 6
    assert(b() === 6)
    a() = 2
    assert(b() === 6)
    a() = 19
    assert(b() === 19)
  }
  "map" in {
    val a = Var(10)
    val b = Rx{ a() + 2 }
    val c = a.map(_*2)
    val d = b.map(_+3)
    assert(c() === 20)
    assert(d() === 15)
    a() = 1
    assert(c() === 2)
    assert(d() === 6)
  }
  "debounce" in {
    val a = Var(10)
    val b = a.debounce(50 millis)
    val c = Rx( a() * 2 ).debounce(50 millis)
    var count = 0
    val ob = Obs(b){ count += 1 }
    val oa = Obs(c){ count += 1 }

    a() = 5
    assert(b() === 5)
    assert(c() === 10)
    a() = 2
    assert(b() === 5)
    assert(c() === 10)
    a() = 4
    assert(b() === 5)
    assert(c() === 10)
    a() = 7
    assert(b() === 5)
    assert(c() === 10)
    eventually{
      assert(b() === 7)
      assert(c() === 14)
    }

  }
}