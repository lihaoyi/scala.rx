package rx
import util.{Failure, Success}

import utest._
import acyclic.file
object BasicTests extends TestSuite{

  //We dont care about potential Rx leaks in BasicTest
  implicit val testctx = RxCtx.Unsafe

  def tests = TestSuite{
    "sigTests" - {
      "basic" - {
        "rxHelloWorld" - {
          val a = Var(1); val b = Var(2)
          val c = Rx.build{i: RxCtx=> a()(i) + b()(i) }
          assert(c.now == 3)
          a() = 4
          assert(c.now == 6)
        }
        "toRx" - {
          val a = Var(1)
          val b = a.toRx
          a() = 2
          assert(b.now == 2)
        }
        "ordering" - {
          var changes = ""
          val a = Var(1)
          val b = Rx{ changes += "b"; a() + 1 }
          val c = Rx{ changes += "c"; a() + b() }
          assert(changes == "bc")
          a() = 4
          assert(changes == "bcbc")
        }
        "options"-{
          val a = Var[Option[Int]](None)
          val b = Var[Option[Int]](None)
          val c = Rx {
            a().flatMap{ x =>
              b().map{ y =>
                x + y
              }
            }
          }
          a() = Some(1)
          b() = Some(2)
          assert (c.now == Some(3))
        }
      }
      "languageFeatures" - {
        "patternMatching" - {
          val a = Var(1); val b = Var(2)
          val c = Rx{
            a() match{
              case 0 => b()
              case x => x
            }
          }
          assert(c.now == 1)
          a() = 0
          assert(c.now == 2)
        }
        "implicitConversions" - {
          val a = Var(1); val b = Var(2)
          val c = Rx{
            val t1 = a() + " and " + b()
            val t2 = a() to b()
            t1 + ": " + t2
          }
          assert(c.now == "1 and 2: Range(1, 2)")
          a() = 0
          assert(c.now == "0 and 2: Range(0, 1, 2)")
        }
        "useInByNameParameters" - {
          val a = Var(1)

          val b = Rx{ Some(1).getOrElse(a()) }
          assert(b.now == 1)
        }
      }
    }

    "obsTests" - {
      "helloWorld" - {
        val a = Var(1)
        var count = 0
        val o = a.trigger{
          count = a.now + 1
        }
        assert(count == 2)
        a() = 4
        assert(count == 5)
      }
      "skipInitial" - {
        val a = Var(1)
        var count = 0
        val o = a.triggerLater{
          count = count + 1
        }
        assert(count == 0)
        a() = 2
        assert(count == 1)
      }

      "simpleExample" - {
        val a = Var(1)
        val b = Rx{ a() * 2 }
        val c = Rx{ a() + 1 }
        val d = Rx{ b() + c() }
        var bS = 0;     val bO = b.trigger{ bS += 1 }
        var cS = 0;     val cO = c.trigger{ cS += 1 }
        var dS = 0;     val dO = d.trigger{ dS += 1 }

        assert(bS == 1);   assert(cS == 1);   assert(dS == 1)

        a() = 2
        assert(bS == 2);   assert(cS == 2);   assert(dS == 2)

        a() = 1
        assert(bS == 3);   assert(cS == 3);   assert(dS == 3)
      }
      'killing{
        val a = Var(1)
        var i = 0
        val o = a.trigger(i += 1)
        assert(
          a.Internal.observers.size == 1,
          i == 1
        )
        a() = 2
        assert(i == 2)
        o.kill()
        a() = 3
        assert(
          a.Internal.observers.size == 0,
          i == 2
        )

      }
    }

    "errorHandling" - {
      "simpleCatch" - {
        val a = Var(1L)
        val b = Rx{ 1 / a() }
        assert(b.now == 1)
        assert(b.toTry == Success(1L))
        a() = 0
        intercept[Exception]{
          b.now
        }
        assertMatch(b.toTry){case Failure(_) =>}
      }
      "longChain" - {
        val a = Var(1L)
        val b = Var(2L)

        val c = Rx{ a() / b() }
        val d = Rx{ a() * 5 }
        val e = Rx{ 5 / b() }
        val f = Rx{ a() + b() + 2 }
        val g = Rx{ f() + c() }

        assertMatch(c.toTry){case Success(0)=>}
        assertMatch(d.toTry){case Success(5)=>}
        assertMatch(e.toTry){case Success(2)=>}
        assertMatch(f.toTry){case Success(5)=>}
        assertMatch(g.toTry){case Success(5)=>}

        b() = 0

        assertMatch(c.toTry){case Failure(_)=>}
        assertMatch(d.toTry){case Success(5)=>}
        assertMatch(e.toTry){case Failure(_)=>}
        assertMatch(f.toTry){case Success(3)=>}
        assertMatch(g.toTry){case Failure(_)=>}
      }
    }
    'dontPropagateIfUnchanged{
      val a = Var(1)
      var ai = 0
      var thing = 0
      val b = Rx{ math.max(a(), 0) + thing }
      var bi = 0
      val c = Rx{ b() / 2 }
      var ci = 0

      a.trigger{ ai += 1 }
      b.trigger{ bi += 1 }
      c.trigger{ ci += 1 }
      // if c doesn't change (because of rounding) don't update ci
      assert(ai == 1, bi == 1, ci == 1)
      a() = 0
      assert(ai == 2, bi == 2, ci == 1)
      a() = 1
      assert(ai == 3, bi == 3, ci == 1)
      // but if c changes then update ci
      a() = 2
      assert(ai == 4, bi == 4, ci == 2)

      // if a doesn't change, don't update anything
      a() = 2
      assert(ai == 4, bi == 4, ci == 2)

      // if b doesn't change, don't update bi or ci
      a() = 0
      assert(ai == 5, bi == 5, ci == 3)
      a() = -1
      assert(ai == 6, bi == 5, ci == 3)

      // all change then all update
      a() = 124
      assert(ai == 7, bi == 6, ci == 4)

      // recalcing with no change means no update
      b.recalc()
      assert(ai == 7, bi == 6, ci == 4)

      // recalcing with change (for whatever reason) updates downstream
      thing = 12
      b.recalc()
      assert(ai == 7, bi == 7, ci == 5)
    }
    'printing{
      val v = Var(1).toString
      val r = Rx(1).toString
      assert(
        v.startsWith("rx.Var@"),
        v.endsWith("(1)"),
        r.startsWith("rx.Rx@"),
        r.endsWith("(1)")
      )
    }

  }
}