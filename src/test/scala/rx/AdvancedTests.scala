package rx

import org.scalatest._
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.Eventually
import akka.actor.ActorSystem
import time.{Millis, Span}

class AdvancedTests extends FreeSpec{
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