package rx

import org.scalatest._
import util.{Try, Success, Failure}
import time.{Millis, Span}

class AdvancedTests extends FreeSpec{
  implicit val prop = Propagator.Immediate
  "nesting" - {
    "nested Rxs" in {
      val a = Var(1)
      val b = Rx{
        Rx{ a() } -> Rx{ math.random }
      }
      val r = b()._2()
      a() = 2
      assert(b()._2() === r)
    }
    "WebPage" in {
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

      assert(page().html() === "Home Page! time: 123")

      fakeTime = 234
      page().update()
      assert(page().html() === "Home Page! time: 234")

      fakeTime = 345
      url() = "www.mysite.com/about"
      assert(page().html() === "About Me, time: 345")

      fakeTime = 456
      page().update()
      assert(page().html() === "About Me, time: 456")
    }

  }

  "combinators" - {
    "foreach" in {
      val a = Var(1)
      var count = 0
      val o = a.foreach{ x =>
        count = x + 1
      }
      assert(count === 2)
      a() = 4
      assert(count === 5)
    }
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

    "mapAll" in {
      val a = Var(10)
      val b = Rx{ 100 / a() }
      val c = b.mapAll{
        case Success(x) => Success(x * 2)
        case Failure(_) => Success(1337)
      }
      val d = b.mapAll{
        case Success(x) => Failure(new Exception("No Error?"))
        case Failure(x) => Success(x.toString)
      }
      assert(c() === 20)
      assert(d.toTry.isFailure)
      a() = 0
      assert(c() === 1337)
      assert(d.toTry === Success("java.lang.ArithmeticException: / by zero"))
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

    "filterAll" in {
      val a = Var(10)
      val b = Rx{ 100 / a() }
      val c = b.filterAll{_.isSuccess}
      assert(c() === 10)
      a() = 9
      assert(c() === 11)
      a() = 0
      assert(c() === 11)
      a() = 1
      assert(c() === 100)
    }

    "reduce" in {
      val a = Var(1)
      val b = a.reduce(_ * _)
      a() = 2
      assert(b() === 2)
      a() = 3
      assert(b() === 6)
      a() = 4
      assert(b() === 24)
    }

    "reduceAll" in {
      val a = Var(1)
      val b = Rx{ 100 / a() }
      val c = b.reduceAll[Int]{
        case (Success(a), Success(b)) => Success(a + b)
        case (Failure(a), Failure(b)) => Success(1337)
        case (Failure(a), Success(b)) => Failure(a)
        case (Success(a), Failure(b)) => Failure(b)
      }
      assert(c() === 100)
      a() = 0
      assert(c.toTry.isFailure)
      a() = 10
      assert(c.toTry.isFailure)
      a() = 100
      assert(c.toTry.isFailure)
      a() = 0
      assert(c() === 1337)
      a() = 10
      assert(c() === 1347)
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

}