package rx
import acyclic.file

import utest._
object AdvancedTests extends TestSuite{

  //Using a static scope for some tests to check the more common use case of the Rx macro
  object MacroDoesRightThing {
    var top1Count = 0
    var top2Count = 0
    var inner1Count = 0
    var inner2Count = 0

    def chk() = {
      assert(inner1Count == inner2Count)
      assert(top1Count == top2Count)
    }

    val top = Var(List(1,2,3))
    val other = Var(1)

    def inner1(num: Int)(implicit topCtx: Ctx.Owner) = Rx.build { (owner, data) =>
      inner1Count += 1
      other()(data)
    }(topCtx)

    def inner2(num: Int)(implicit topCtx: Ctx.Owner) = Rx {
      inner2Count += 1
      other()
    }

    val things1 = Rx {
      top1Count += 1
      top().map(a => inner1(a))
    }

    val things2 = Rx {
      top2Count += 1
      top().map(a => inner2(a))
    }
  }

  object SafeTrait {
    trait RxTrait {
      implicit def ctx: Ctx.Owner // leave ctx abstract
      val aa = Var(1)
      lazy val meh = Rx { aa() }
    }

    object RxObj extends RxTrait {
      //override implicit val ctx = Ctx.Owner.Unsafe
      override implicit val ctx = Ctx.Owner.safe()
      val objRx = Rx { meh() }
    }

    class RxClass extends RxTrait {
      //compile time error:
      //override implicit val ctx = RxCtx.safe()
      override implicit val ctx = Ctx.Owner.Unsafe
      val classRx = Rx { meh() }
    }

    //One other way RxCtx.safe() can be used
    class RxClass2()(implicit ZZZ: Ctx.Owner) extends RxTrait {
      override val ctx = Ctx.Owner.safe()
    }

    //Although this would be more normal
    class RxClass3()(implicit override val ctx: Ctx.Owner) extends RxTrait {
    }
  }

  def tests = utest.Tests {
    'perf{
//      'init{
//        val start = System.currentTimeMillis()
//        var n = 0
//        while(System.currentTimeMillis() < start + 10000){
//          val (a, b, c, d, e, f) = Utils.initGraph
//          n += 1
//        }
//        n
//      }
//      'propagations{
//        val (a, b, c, d, e, f) = Utils.initGraph
//        val start = System.currentTimeMillis()
//        var n = 0
//
//        while(System.currentTimeMillis() < start + 10000){
//          a() = n
//          n += 1
//        }
//        n
//      }
    }
    "nesting" - {
      "nestedRxs" - {
        import Ctx.Owner.Unsafe._

        val a = Var(1)
        val b = Rx{
          Rx{ a() } -> Rx{ math.random }
        }
        val r = b.now._2.now
        a() = 2
        assert(b.now._2.now == r)
      }
      "macroDoesTheRightThing" - {
        import MacroDoesRightThing._
        chk()
        other() = other.now + 1
        top() = List(3,2,1)
        top() = List(2,2,2)
        top() = List(1,2,3)
        other() = other.now + 1
        chk()
      }
      "safeTrait" - {
        import SafeTrait._
        RxObj.aa() = 3
        assert(RxObj.meh.now == 3)
        assert(RxObj.objRx.now == 3)

        val instance = new RxClass
        assert(instance.meh.now == 1)
        instance.aa() = 5
        assert(instance.classRx.now == 5)

        val instance2 = new RxClass2()(Ctx.Owner.Unsafe)
        assert(instance2.meh.now == 1)

        val instance3 = new RxClass3()(Ctx.Owner.Unsafe)
        instance3.aa() = 42
        assert(instance3.meh.now == 42)
      }
      "recalc" - {
        import Ctx.Owner.Unsafe._

        var source = 0
        val a = Rx{
          source
        }
        var i = 0
        val o = a.trigger{
          i += 1
        }
        assert(i == 1)
        assert(a.now == 0)
        source = 1
        assert(a.now == 0)
        a.recalc()
        assert(a.now == 1)
        assert(i == 2)
      }
      "multiset" - {
        import Ctx.Owner.Unsafe._

        val a = Var(1)
        val b = Var(1)
        val c = Var(1)
        val d = Rx{
          a() + b() + c()
        }
        var i = 0
        val o = d.trigger{
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
        import Ctx.Owner.Unsafe._

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
        assert(page.now.html.now == "Home Page! time: 123")

        fakeTime = 234
        page.now.update()
        assert(page.now.html.now == "Home Page! time: 234")

        fakeTime = 345
        url() = "www.mysite.com/about"
        assert(page.now.html.now == "About Me, time: 345")

        fakeTime = 456
        page.now.update()
        assert(page.now.html.now == "About Me, time: 456")
      }
    }
    "higherOrderRxs" - {
      import Ctx.Owner.Unsafe._

      val a = Var(1)
      val b = Var(2)
      val c = Rx(Rx(a() + b()) -> (a() - b()))

      assert(
        a.Internal.downStream.size == 2,
        b.Internal.downStream.size == 2,
        c.now._1.now == 3,
        c.now._2 == -1
      )

      a() = 2

      assert(
        a.Internal.downStream.size == 2,
        b.Internal.downStream.size == 2,
        c.now._1.now == 4,
        c.now._2 == 0
      )

      b() = 3

      assert(
        a.Internal.downStream.size == 2,
        b.Internal.downStream.size == 2,
        c.now._1.now == 5,
        c.now._2 == -1
      )
    }

    "leakyRx" - {
      var testY = 0
      var testZ = 0
      val a = Var(10)

//      Correct way to implement a def: Rx[_]
      def y()(implicit zzz: Ctx.Owner) = Rx { testY += 1; a() }

      //This way will leak an Rx (ie exponential blow up in cpu time), but is not caught at compile time
      def z() = Rx.unsafe { testZ += 1; a() }

      val yy = Rx.unsafe { a() ; for (i <- 0 until 100) yield y() }
      val zz = Rx.unsafe { a() ; for (i <- 0 until 100) yield z() }
      a() = 1
      a() = 2
      a() = 3
      a() = 4
      assert(testY == 500)
      assert(testZ == 1500)
    }

    "leakyObs" - {
      var testY = 0
      var testZ = 0
      val a = Var(10)

      //Correct way to implement a def: Obs
      def y()(implicit zzz: Ctx.Owner) = a.trigger(testY += 1)


      //This way will leak the Obs (ie exponential blow up in cpu time), but is not caught at compile time
      def z() = a.trigger(testZ += 1)(Ctx.Owner.Unsafe)

      val yy = Rx.unsafe { a() ; for (i <- 0 until 100) yield y() }
      val zz = Rx.unsafe { a() ; for (i <- 0 until 100) yield z() }
      a() = 1
      a() = 2
      a() = 3
      a() = 4
      assert(testY == 500)
      assert(testZ == 1500)
    }

  }
}
