package rx

import util.{Success, Failure}
import rx.core.Propagator
import acyclic.file
import ops._
import utest._
object AdvancedTests extends TestSuite{
  implicit val prop = Propagator.Immediate
  def tests = TestSuite{
    "nesting" - {
      "nestedRxs" - {
        val a = Var(1)
        val b = Rx{
          Rx{ a() } -> Rx{ math.random }
        }
        val r = b()._2()
        a() = 2
        assert(b()._2() == r)
      }
      "recalc" - {
        var source = 0
        val a = Rx{
          source
        }
        var i = 0
        val o = Obs(a){
          i += 1
        }
        assert(i == 1)
        assert(a() == 0)
        source = 1
        assert(a() == 0)
        a.recalc()
        assert(a() == 1)
        assert(i == 2)
      }
      "multiset" - {
        val a = Var(1)
        val b = Var(1)
        val c = Var(1)
        val d = Rx{
          a() + b() + c()
        }
        var i = 0
        val o = Obs(d){
          i += 1
        }
        assert(i == 1)
        a() = 2
        assert(i == 2)
        b() = 2
        assert(i == 3)
        c() = 2
        assert(i == 4)

        Var.set(
          a -> 3,
          b -> 3,
          c -> 3
        )

        assert(i == 5)

        Var.set(
          Seq(
            a -> 4,
            b -> 5,
            c -> 6
          ):_*
        )

        assert(i == 6)
      }
      "webPage" - {
        var fakeTime = 123
        trait WebPage{
          def fTime = fakeTime
          val time = Var(fTime)
          def update(): Unit  = time() = fTime
          val html: Rx[String]
        }
        class HomePage extends WebPage {
          val html = Rx{"Home Page! time: " + time()}
        }
        class AboutPage extends WebPage {
          val html = Rx{"About Me, time: " + time()}
        }

        val url = Var("www.mysite.com/home")
        val page = Rx{
          url() match{
            case "www.mysite.com/home" => new HomePage()
            case "www.mysite.com/about" => new AboutPage()
          }
        }

        assert(page().html() == "Home Page! time: 123")

        fakeTime = 234
        page().update()
        assert(page().html() == "Home Page! time: 234")

        fakeTime = 345
        url() = "www.mysite.com/about"
        assert(page().html() == "About Me, time: 345")

        fakeTime = 456
        page().update()
        assert(page().html() == "About Me, time: 456")
      }

    }

    "combinators" - {
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
      "skipFailure" - {
        val x = Var(10L)
        val y = Rx{ 100 / x() }.skipFailures
        val z = Rx{ y() + 20 }
        assert(y() == 10)
        assert(z() == 30)
        x() = 0
        assert(y() == 10)
        assert(z() == 30)
        x() = 5
        assert(y() == 20)
        assert(z() == 40)
      }

      "map" - {
        val a = Var(10)
        val b = Rx{ a() + 2 }
        val c = a.map(_*2)
        val d = b.map(_+3)
        assert(c() == 20)
        assert(d() == 15)
        a() = 1
        assert(c() == 2)
        assert(d() == 6)
      }

      "mapAll" - {
        val a = Var(10L)
        val b = Rx{ 100 / a() }
        val c = b.mapAll{
          case Success(x) => Success(x * 2)
          case Failure(_) => Success(1337)
        }
        val d = b.mapAll{
          case Success(x) => Failure(new Exception("No Error?"))
          case Failure(x) => Success(x.toString)
        }
        assert(c() == 20)
        assert(d.toTry.isFailure)
        a() = 0
        assert(c() == 1337)
        assert(d.toTry == Success("java.lang.ArithmeticException: / by zero"))
      }

      "filter" - {
        val a = Var(10)
        val b = a.filter(_ > 5)
        a() = 1
        assert(b() == 10)
        a() = 6
        assert(b() == 6)
        a() = 2
        assert(b() == 6)
        a() = 19
        assert(b() == 19)
      }

      "filterAll" - {
        val a = Var(10L)
        val b = Rx{ 100 / a() }
        val c = b.filterAll{_.isSuccess}
        assert(c() == 10)
        a() = 9
        assert(c() == 11)
        a() = 0
        assert(c() == 11)
        a() = 1
        assert(c() == 100)
      }

      "reduce" - {
        val a = Var(1)
        val b = a.reduce(_ * _)
        a() = 2
        assert(b() == 2)
        a() = 3
        assert(b() == 6)
        a() = 4
        assert(b() == 24)
      }

      "reduceAll" - {
        val a = Var(1L)
        val b = Rx{ 100 / a() }
        val c = b.reduceAll[Long]{
          case (Success(a), Success(b)) => Success(a + b)
          case (Failure(a), Failure(b)) => Success(1337)
          case (Failure(a), Success(b)) => Failure(a)
          case (Success(a), Failure(b)) => Failure(b)
        }
        assert(c() == 100)
        a() = 0
        assert(c.toTry.isFailure)
        a() = 10
        assert(c.toTry.isFailure)
        a() = 100
        assert(c.toTry.isFailure)
        a() = 0
        assert(c() == 1337)
        a() = 10
        assert(c() == 1347)
      }
    }

    "zipper" - {
      val a = Var(1)
      val b = a.zip
      val c = a.zip{case (a,b)=>s"$a => $b"}
      a() = 2
      assert(b() == (1,2))
      assert(c() == "1 => 2")
      a() = 3
      assert(b() == (2,3))
      assert(c() == "2 => 3")
      a() = 4
      assert(b() == (3,4))
      assert(c() == "3 => 4")
    }


    "kill" - {
      "killObs" - {
        val a = Var(1)
        val b = Rx{ 2 * a() }
        var target = 0
        val o = Obs(b){
          target = b()
        }

        assert(a.children == Set(b))
        assert(b.children == Set(o))

        assert(target == 2)
        a() = 2
        assert(target == 4)
        o.kill()

        assert(a.children == Set(b))
        assert(b.children == Set())

        a() = 3
        assert(target == 4)
      }

      "killRx" - {
        val (a, b, c, d, e, f) = Util.initGraph

        assert(c() == 3)
        assert(e() == 7)
        assert(f() == 26)
        a() = 3
        assert(c() == 5)
        assert(e() == 9)
        assert(f() == 38)

        // Killing d stops it from updating, but the changes can still
        // propagate through e to reach f
        d.kill()
        a() = 1
        assert(f() == 36)

        // After killing f, it stops updating but others continue to do so
        assert(e.children == Set(f))
        f.kill()
        assert(e.children == Set())
        a() = 3
        assert(c() == 5)
        assert(e() == 9)
        assert(f() == 36)

        // After killing c, the everyone doesn't get updates anymore
        assert(a.children == Set(c))
        assert(b.children == Set(c))
        c.kill()
        assert(a.children == Set())
        assert(b.children == Set())
        a() = 1
        assert(c() == 5)
        assert(e() == 9)
        assert(f() == 36)
      }
      "killAllRx" - {
        val (a, b, c, d, e, f) = Util.initGraph

        // killAll-ing d makes f die too
        d.killAll()

        a() = 3
        assert(c() == 5)
        assert(e() == 9)
        assert(f() == 26)
      }
    }
  }
  /*
  "recursion" - {
    "calculating fixed point" - {
      lazy val s: Rx[Double] = Rx{ Math.cos(s()) }
      println(s())
    }
    "calculating sqrt" - {
      lazy val s: Rx[Double] = Rx(default = 10.0){ s() - (s() * s() - 10) / (2 * s()) }
      println(s())
    }
  }
   */

}