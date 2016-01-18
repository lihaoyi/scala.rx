package rx
//
import acyclic.file

import scala.util.{Try, Success, Failure}

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

    def inner1(num: Int)(implicit topCtx: RxCtx) = Rx.build { newCtx =>
      inner1Count += 1
      other()(newCtx)
    }(topCtx)

    def inner2(num: Int)(implicit topCtx: RxCtx) = Rx {
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
      implicit def ctx: RxCtx // leave ctx abstract
      val aa = Var(1)
      lazy val meh = Rx { aa() }
    }

    object RxObj extends RxTrait {
      //override implicit val ctx = RxCtx.Unsafe
      override implicit val ctx = RxCtx.safe()
      val objRx = Rx { meh() }
    }

    class RxClass extends RxTrait {
      //compile time error:
      //override implicit val ctx = RxCtx.safe()
      override implicit val ctx = RxCtx.Unsafe
      val classRx = Rx { meh() }
    }

    //One other way RxCtx.safe() can be used
    class RxClass2()(implicit ZZZ: RxCtx) extends RxTrait {
      override val ctx = RxCtx.safe()
    }

    //Although this would be more normal
    class RxClass3()(implicit override val ctx: RxCtx) extends RxTrait {
    }
  }

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

//    val thing = c.filter(_() >= 10)
//      .map { m => "x"*(m.now/2) }
//      .flatMap(s => Rx { s.length + b() })
//      .fold(List.empty[Int])((acc,elem) => elem::acc)
//
//    assert(thing.now == List(6))
//    a() = 2
//    assert(thing.now == List(6))
//    a() = 12
//    assert(thing.now == List(12,6))
//    b() = 100
//    assert(thing.now == List(106,12,6))
//    a() = 18
//    assert(thing.now == List(109,106,12,6))
//    a() = 10
//    assert(thing.now == List(105,109,106,12,6))
//    a() = 20
//    assert(thing.now == List(110,105,109,106,12,6))

    def wat(): Unit = ()
  }

  def tests = TestSuite {
//    'perf{
//      'init{
//        val start = System.currentTimeMillis()
//        var n = 0
//        while(System.currentTimeMillis() < start + 10000){
//          val (a, b, c, d, e, f) = Util.initGraph
//          n += 1
//        }
//        n
//      }
//      'propagations{
//        val (a, b, c, d, e, f) = Util.initGraph
//        val start = System.currentTimeMillis()
//        var n = 0
//
//        while(System.currentTimeMillis() < start + 10000){
//          a() = n
//          n += 1
//        }
//        n
//      }
//    }
    "nesting" - {
      "nestedRxs" - {
        implicit val testctx = RxCtx.Unsafe

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

        val instance2 = new RxClass2()(RxCtx.Unsafe)
        assert(instance2.meh.now == 1)

        val instance3 = new RxClass3()(RxCtx.Unsafe)
        instance3.aa() = 42
        assert(instance3.meh.now == 42)
      }
      "recalc" - {
        implicit val testctx = RxCtx.Unsafe

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
        implicit val testctx = RxCtx.Unsafe

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
        implicit val testctx = RxCtx.Unsafe

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


    "combinators" - {
      implicit val testctx = RxCtx.Unsafe
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
      "higherOrderMap" - {
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
        assert(va.now == "a"*2)
        vv() = Rx(Var(3))
        assert(va.now == "a"*3)
        vv() = Rx(Var(4))
        assert(va.now == "a"*4)
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
      "higherOrderFlatMap" - {
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
        assert(aa.now == 11)
        val c1 = innerCount
        vv.now.now() = 10
        val c2 = innerCount
        assert(aa.now == 11 && c1 == c2)
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
      "higherOrderFilter" - {
        val v = Var(Var(1))
        val a: Rx[Var[Int]] = v.filter(_() % 2 == 1)
        val b = a.all.filter(_.toOption.exists(_() % 5 == 0))
        v.now() = 2
        assert(a.now.now == 1)
        assert(b.now.now == 1)
        v.now() = 3
        assert(a.now.now == 3)
        assert(b.now.now == 1)
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
        assert(zz.now.now.now == 1)
        vv.now.now() = 4
        assert(zz.now.now.now == 1)
        vv.now.now() = 3
        assert(zz.now.now.now == 3)
        vv.now() = Var(5)
        assert(zz.now.now.now == 5)
        vv.now() = Var(6)
        assert(zz.now.now.now == 5)
        vv() = Var(Var(7))
        assert(zz.now.now.now == 7)
        vv() = Var(Var(8))
        assert(zz.now.now.now == 7)
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
      "higherOrderReduce" - {
        val v = Var(Var(1))
        val reduced = v.reduce { (prev,next) => Var(prev.now+next.now) }
        assert(reduced.now.now == 1)
        //No recalc, Var has same value and does not update
        v.now() = 1
        assert(reduced.now.now == 1)
        v.now() = 4
        assert(reduced.now.now == 5)
        v.now() = 5
        assert(reduced.now.now == 10)
        v() = Var(10)
        assert(reduced.now.now == 20)
      }
      "higherOrderAllReduce" - {
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
        assert(reduced.now.now == 6)
        v.now() = 3
        assert(reduced.now.now == 6)
        v.now() = 6
        assert(reduced.now.now == 12)
      }
      "fold" - {
        val a = Var(2)
//        val x = rx.Macros.foldImpl[Int, List[Int], List[Int]](
//          (rxctx$macro$246: RxCtx) => collection.immutable.List.empty[Int],
//          (rxctx$macro$246: RxCtx) => rx.GenericOps[Int](a).node
//        )(
//          (rxctx$macro$246: RxCtx) => (acc: List[Int], elem: Int) => {
//          val x$22: Int = elem;
//            acc.::[Int](x$22)
//          },
//          ((rxctx$macro$246: RxCtx) => rx.Node.getDownstream(rx.GenericOps[Int](a).node)),
//          testctx,
//          ((x$72) => x$72.now),
//          ((x) => x)
//        )

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
      "higherOrderFold" - {
        val v = Var(Var(1))
        val folded = v.fold(List.empty[Var[Int]]) { (prev,next) => Var(next.now) :: prev }
        assert(folded.now.map(_.now) == List(1))
        //No recalc, Var has same value and does not update
        v.now() = 1
        assert(folded.now.map(_.now) == List(1))
        v.now() = 4
        assert(folded.now.map(_.now) == List(4,1))
        v.now() = 5
        assert(folded.now.map(_.now) == List(5,4,1))
        v() = Var(10)
        assert(folded.now.map(_.now) == List(10,5,4,1))
      }

      "higherOrderAllFold" - {
        val rv = Var(Rx(Var(0)))
        val folded = rv.toRx.all.fold(Try(List.empty[Int])) {
          case (Success(prev),Success(next)) =>
            if(next.now.now % 2 == 0) Success(next.now.now :: prev)
            else Success(prev)
          case (prev, _)=> prev
        }
        assert(folded.now == List(0))
        rv.now.now() = 2
        assert(folded.now == List(2,0))
        rv.now.now() = 4
        assert(folded.now == List(4,2,0))
        rv() = Rx(Var(3))
        assert(folded.now == List(4,2,0))
        rv() = Rx(Var(6))
        assert(folded.now == List(6,4,2,0))
      }
      "killRx" - {
        val (a, b, c, d, e, f) = Util.initGraph()

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
    "higherOrderRxs" - {
      implicit val testctx = RxCtx.Unsafe

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
    "compileTimeChecks" - {
      "simpleDef" - {
        compileError("def fail() = Rx { }")
      }
      "nestedDef" - {
        compileError("object Fail { def fail() = Rx { } }")
      }
      "nestedSafeCtx" - {
        compileError("object Fail { def fail() = { implicit val ctx = RxCtx.safe() ; Rx { } } }")
      }
      "simpleUnsafeDef" - {
        //heh
        compileError("""compileError("def ok() = Rx.unsafe { }")""")
      }
      "nestedUnsafeCtx" - {
        compileError("""compileError("object Fail { def fail() = { implicit val ctx = RxCtx.Unsafe ; Rx { } } }")""")
      }
    }
    "leakyRxCtx" - {
      var testY = 0
      var testZ = 0
      val a = Var(10)

      //Correct way to implement a def: Rx[_]
      def y()(implicit zzz: RxCtx) = Rx { testY += 1; a() }

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
  }
}
