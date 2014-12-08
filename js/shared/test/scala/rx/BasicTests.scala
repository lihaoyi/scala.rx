package rx
import util.{Failure, Success}

import utest._
import acyclic.file
object BasicTests extends TestSuite{
  def tests = TestSuite{
    "sigTests" - {
      "basic" - {
        "rxHelloWorld" - {
          val a = Var(1); val b = Var(2)
          val c = Rx{ a() + b() }
          assert(c() == 3)
          a() = 4
          assert(c() == 6)
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
          assert (c() == Some(3))
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
          assert(c() == 1)
          a() = 0
          assert(c() == 2)
        }
        "implicitConversions" - {
          val a = Var(1); val b = Var(2)
          val c = Rx{
            val t1 = a() + " and " + b()
            val t2 = a() to b()
            t1 + ": " + t2
          }
          assert(c() == "1 and 2: Range(1, 2)")
          a() = 0
          assert(c() == "0 and 2: Range(0, 1, 2)")
        }
        "useInByNameParameters" - {
          val a = Var(1)

          val b = Rx{ Some(1).getOrElse(a()) }
          assert(b() == 1)
        }
      }
    }

    "obsTests" - {
      "helloWorld" - {
        val a = Var(1)
        var count = 0
        val o = a.trigger{
          count = a() + 1
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
    }

    "errorHandling" - {
      "simpleCatch" - {
        val a = Var(1L)
        val b = Rx{ 1 / a() }
        assert(b() == 1)
        assert(b.toTry == Success(1L))
        a() = 0
        intercept[Exception]{
          b()
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

  }
}