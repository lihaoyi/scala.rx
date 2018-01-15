package rx

import utest._

import scala.util.{Failure, Success, Try}
import collection.mutable

object CombinatorTests extends TestSuite{

  object TopLevelVarCombinators {
    val aa = Var(1)

    val mapped = aa.map(_ + 10)

    val filtered = aa.filter(_ % 2 == 0)

    val reduced = aa.reduce((a,b) => a+b)
  }

  object MoarCombinators {
    val a = Var(1)
    val b = Var(6)
    val c: Var[Var[Int]] = Var(a)

    val thing = c.filter(_() >= 10)
                 .map { m => "x"*(m.now/2) }
                 .flatMap(s => Rx { s.length + b() })
                 .fold(List.empty[Int])((acc,elem) => elem::acc)

    assert(thing.now == List(6))
    a() = 2
    assert(thing.now == List(6))
    a() = 12
    assert(thing.now == List(6))
    b() = 100
    assert(thing.now == List(100,6))
    a() = 18
    assert(thing.now == List(100,6))
    c() = Var(10)
    assert(thing.now == List(105, 100,6))
    a() = 20
    assert(thing.now == List(105, 100,6))

    def wat(): Unit = ()
  }

  def tests = utest.Tests {
    "combinators" - {
      import Ctx.Owner.Unsafe._
      "foreach" - {
        val a = Var(1)
        var count = 0
        val o = a.foreach{ x =>
          count = x + 1
        }
        assert(count == 2)
        a() = 4
        assert(count == 5)
      }
      "map" - {
        val a = Var(10)
        val b = Rx{ a() + 2 }
        val c = a.map(_*2)
        val d = b.map(_+3)
        val e = a.map(_*2).map(_+3)
        assert(c.now == 20)
        assert(d.now == 15)
        assert(e.now == 23)
        a() = 1
        assert(c.now == 2)
        assert(d.now == 6)
        assert(e.now == 5)
      }
      "mapObs" - {
        val v1 = Var(0)
        val v2 = v1.map(identity)
        val v3 = v1.map(identity).map(identity).map(identity)
        def q(implicit trackDependency: Ctx.Data) = {
          if (v1() == 0) v2()
          else {
            if (v3() != v2())
              103
            else
              17
          }
        }
        val v = Rx { q }
        var result = List.empty[Int]
        val obs = v.trigger { result = result :+ v.now }
        assert(result == List(0))
        v1() = 1
        assert(result == List(0,17))
        v1() = 2
        assert(result == List(0,17))
        v1() = 3
        assert(result == List(0,17))
      }
      "mapAll" - {
        val a = Var(10L)
        val b = Rx{ 100 / a() }
        val c = b.all.map{
          case Success(x) => Success(x * 2)
          case Failure(_) => Success(1337)
        }
        val d = b.all.map{
          case Success(x) => Failure(new Exception("No Error?"))
          case Failure(x) => Success(x.toString)
        }
        assert(c.now == 20)
        assert(d.toTry.isFailure)
        a() = 0
        assert(c.now == 1337)
        assert(d.toTry == Success("java.lang.ArithmeticException: / by zero"))
      }
      "flatMapForComprehension" - {
        val a = Var(10)
        val b = for {
          aa <- a
          bb <- Rx { a() + 5}
          cc <- Var(1).map(_*2)
        } yield {
          aa + bb + cc
        }
        assert(b.now == 10 + 15 + 2)
        a() = 100
        assert(b.now == 100 + 105 + 2)
      }
      "flatMapDiamondCase" - {
        val rxa = Var(2)
        val rxb = rxa.map(_ + 1)
        val rxc = rxa.map(_ + 1)

        val rxTriggered = mutable.ArrayBuffer.empty[(Int,Int)]
        Rx {
          val b = rxb()
          val c = rxc()
          rxTriggered += ((b,c))
        }


        val flatMapTriggered = mutable.ArrayBuffer.empty[(Int,Int)]
        for {
          b <- rxb
          c <- rxc
        } yield { 
          flatMapTriggered += ((b,c)) 
        }

        assert(rxTriggered.toList       == List((3,3)))
        assert(flatMapTriggered.toList  == List((3,3)))

        rxa() = 12
        assert(rxTriggered.toList       == List((3,3), (13,13)))
        assert(flatMapTriggered.toList  == List((3,3), (13,13)))
        
        rxa() = 22
        assert(rxTriggered.toList       == List((3,3), (13,13), (23,23)))
        assert(flatMapTriggered.toList  == List((3,3), (13,13), (23,23)))

        rxa() = 32
        assert(rxTriggered.toList       == List((3,3), (13,13), (23,23), (33,33)))
        assert(flatMapTriggered.toList  == List((3,3), (13,13), (23,23), (33,33)))
      }
      "flatMapVar" - {
        val a = Var(0)
        val b = a.flatMap(a => Var(Option.empty[String]))
        assert(b.now == Option.empty[String])
      }
      "filter" - {
        val a = Var(10)
        val b = a.filter(_ > 5)
        a() = 1
        assert(b.now == 10)
        a() = 6
        assert(b.now == 6)
        a() = 2
        assert(b.now == 6)
        a() = 19
        assert(b.now == 19)
      }
      "filterFirstFail" - {
        val a = Var(10)
        val b = a.filter(_ > 15)
        a() = 1
        assert(b.now == 10)
      }
      "filterAll" - {
        val a = Var(10L)
        val b = Rx{ 100 / a() }
        val c = b.all.filter(_.isSuccess)

        assert(c.now == 10)
        a() = 9
        assert(c.now == 11)
        a() = 0
        assert(c.now == 11)
        a() = 1
        assert(c.now == 100)
      }

      "reduce" - {
        val a = Var(2)
        val b = a.reduce(_ * _)
        // no-change means no-change
        a() = 2
        assert(b.now == 2)
        // only does something when you change
        a() = 3
        assert(b.now == 6)
        a() = 4
        assert(b.now == 24)
      }
      "reduceAll" - {
        val a = Var(1L)
        val b = Rx{ 100 / a() }
        val c = b.all.reduce{
          case (Success(a), Success(b)) => Success(a + b)
          case (Failure(a), Failure(b)) => Success(1337)
          case (Failure(a), Success(b)) => Failure(a)
          case (Success(a), Failure(b)) => Failure(b)
        }
        assert(c.now == 100)
        a() = 0
        assert(c.toTry.isFailure)
        a() = 10
        assert(c.toTry.isFailure)
        a() = 100
        assert(c.toTry.isFailure)
        a() = 0
        assert(c.now == 1337)
        a() = 10
        assert(c.now == 1347)
      }


      "fold" - {
        val a = Var(2)
        val b = a.fold(List.empty[Int])((acc,elem) => elem :: acc)
        assert(b.now == List(2))
        // no-change means no-change
        a() = 2
        assert(b.now == List(2))
        // only does something when you change
        a() = 3
        assert(b.now == List(3,2))
        a() = 4
        assert(b.now == List(4,3,2))
      }

      "foldAll" - {
        val a = Var(1L)
        val b = Rx{ 100 / a() }
        val c = b.all.fold(Try(List.empty[Long])) {
          case (Success(a), Success(b)) => Success(b :: a)
          case (Failure(a), Failure(b)) => Success(List(1337))
          case (Failure(a), Success(b)) => Failure(a)
          case (Success(a), Failure(b)) => Failure(b)
        }
        assert(c.now == List(100))
        a() = 0
        assert(c.toTry.isFailure)
        a() = 10
        assert(c.toTry.isFailure)
        a() = 100
        assert(c.toTry.isFailure)
        a() = 0
        assert(c.now == List(1337))
        a() = 10
        assert(c.now == List(10,1337))
      }

      "killRx" - {
        val (a, b, c, d, e, f) = Utils.initGraph

        assert(c.now == 3)
        assert(e.now == 7)
        assert(f.now == 26)
        a() = 3
        assert(c.now == 5)
        assert(e.now == 9)
        assert(f.now == 38)

        // Killing d stops it from updating, but the changes can still
        // propagate through e to reach f
        d.kill()
        a() = 1
        assert(f.now == 36)

        // After killing f, it stops updating but others continue to do so
        f.kill()
        a() = 3
        assert(c.now == 5)
        assert(e.now == 9)
        assert(f.now == 36)

        // After killing c, the everyone doesn't get updates anymore
        c.kill()
        a() = 1
        assert(c.now == 5)
        assert(e.now == 9)
        assert(f.now == 36)
      }
    }
    'higherOrder - {
      import Ctx.Owner.Unsafe._
      "map" - {
        val v = Var(Var(1))
        val a = v.map(_() + 42)
        assert(a.now == 43)
        v.now() = 100
        assert(a.now == 142)
        v() = Var(3)
        assert(a.now == 45)

        //Ensure this thing behaves in some normal fashion
        val vv = Var(Rx(Var(1)))
        val va = vv.map(aa => "a" * aa.now.now)
        assert(va.now == "a")
        vv.now.now() = 2
        assert(va.now == "a")
        vv() = Rx(Var(3))
        assert(va.now == "aaa")
        vv() = Rx(Var(4))
        assert(va.now == "aaaa")
      }
      "filter" - {
        val v = Var(Var(1))
        val a: Rx[Var[Int]] = v.filter(_() % 2 == 1)
        val b = a.all.filter(_.toOption.exists(_() % 5 == 0))
        v.now() = 2
        assert(a.now.now == 2)
        assert(b.now.now == 2)
        v.now() = 3
        assert(a.now.now == 3)
        assert(b.now.now == 3)
        v() = Var(4)
        assert(a.now.now == 3)
        v() = Var(5)
        assert(a.now.now == 5)
        assert(b.now.now == 5)

        //Ahh
        val vv = Var(Var(Var(1)))
        val zz = vv.filter { q =>
          q()
          q.now()
          q.now.now % 2 == 1
        }
        assert(zz.now.now.now == 1)
        vv.now.now() = 2
        assert(zz.now.now.now == 2)
        vv.now.now() = 4
        assert(zz.now.now.now == 4)
        vv.now.now() = 3
        assert(zz.now.now.now == 3)
        vv.now() = Var(5)
        assert(zz.now.now.now == 5)
        vv.now() = Var(6)
        assert(zz.now.now.now == 6)
        vv() = Var(Var(7))
        assert(zz.now.now.now == 7)
        vv() = Var(Var(8))
        assert(zz.now.now.now == 7)
      }
      "flatMap" - {
        val v = Var(Var(1))
        val a = v.flatMap(_.map("a"*_))
        assert(a.now == "a")
        v.now() = 5
        assert(a.now == "a"*5)
        v() = Var(3)
        assert(a.now == "aaa")
        var innerCount = 0
        val vv = Var(Var(Var(1)))
        val aa = vv.flatMap(_.map{i => innerCount += 1; i.now + 1})
        vv.now() = Var(2)
        assert(aa.now == 3)
        vv.now() = Var(2)
        assert(aa.now == 3)
        vv.now.now() = 10
        assert(aa.now == 3)
        val c1 = innerCount
        vv.now.now() = 10
        val c2 = innerCount
        assert(aa.now == 3 && c1 == c2)
      }
      "reduce" - {
        val v = Var(Var(1))
        val reduced = v.reduce { (prev,next) => Var(prev.now+next.now) }
        assert(reduced.now.now == 1)
        //No recalc, Var has same value and does not update
        v.now() = 1
        assert(reduced.now.now == 1)
        v.now() = 4
        assert(reduced.now.now == 4)
        v.now() = 5
        assert(reduced.now.now == 5)
        v() = Var(10)
        assert(reduced.now.now == 15)
      }
      "allReduce" - {
        val v = Rx(Var(0))
        val reduced = v.all.reduce {
          case (Success(prev),Success(next)) =>
            if(next.now % 2 == 0) Success(Var(next.now+prev.now))
            else Success(prev)
          case (prev, _)=> prev
        }
        v.now() = 2
        assert(reduced.now.now == 2)
        v.now() = 4
        assert(reduced.now.now == 4)
        v.now() = 3
        assert(reduced.now.now == 3)
        v.now() = 6
        assert(reduced.now.now == 6)
      }
      "fold" - {
        val v = Var(Var(1))
        val folded = v.fold(List.empty[Var[Int]]) { (prev,next) => Var(next.now) :: prev }
        assert(folded.now.map(_.now) == List(1))
        //No recalc, Var has same value and does not update
        v.now() = 1
        assert(folded.now.map(_.now) == List(1))
        v.now() = 4
        assert(folded.now.map(_.now) == List(1))
        v.now() = 5
        assert(folded.now.map(_.now) == List(1))
        v() = Var(10)
        assert(folded.now.map(_.now) == List(10, 1))
      }

      "allFold" - {
        val rv = Var(Rx(Var(0)))
        val folded = rv.map(a => a).all.fold(Try(List.empty[Int])) {
          case (Success(prev),Success(next)) =>
            if(next.now.now % 2 == 0) Success(next.now.now :: prev)
            else Success(prev)
          case (prev, _)=> prev
        }
        assert(folded.now == List(0))
        rv.now.now() = 2
        assert(folded.now == List(0))
        rv.now.now() = 4
        assert(folded.now == List(0))
        rv() = Rx(Var(3))
        assert(folded.now == List(0))
        rv() = Rx(Var(6))
        assert(folded.now == List(6, 0))
      }
    }
    "topLevelCombinators" - {
      import TopLevelVarCombinators._
      assert(mapped.now == 11)
      assert(filtered.now == 1)
      assert(reduced.now == 1)
      aa() = 2
      assert(mapped.now == 12)
      assert(filtered.now == 2)
      assert(reduced.now == 3)
      aa() = 3
      assert(mapped.now == 13)
      assert(filtered.now == 2)
      assert(reduced.now == 6)
    }
    "moreCombinators" - {
      MoarCombinators.wat()
    }
  }
}
