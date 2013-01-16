package rx
import org.scalatest._
import util.{Failure, Success}
import scala.concurrent.{Promise, Future}
class BasicTests extends FreeSpec with Inside{


  "sig tests" - {
    "basic" - {
      "signal Hello World" in {
        val a = Var(1); val b = Var(2)
        val c = Rx{ a() + b() }
        assert(c() === 3)
        a() = 4
        assert(c() === 6)
      }
      "long chain" in {
        val a = Var(1) // 3

        val b = Var(2) // 2

        val c = Rx{ a() + b() } // 5
        val d = Rx{ c() * 5 } // 25
        val e = Rx{ c() + 4 } // 9
        val f = Rx{ d() + e() + 4 } // 25 + 9 + 4 =

        assert(f() === 26)
        a() = 3
        assert(f() === 38)
      }
    }
    "language features" - {
      "pattern matching" in {
        val a = Var(1); val b = Var(2)
        val c = Rx{
          a() match{
            case 0 => b()
            case x => x
          }
        }
        assert(c() === 1)
        a() = 0
        assert(c() === 2)
      }
      "implicit conversions" in {
        val a = Var(1); val b = Var(2)
        val c = Rx{
          val t1 = a() + " and " + b()
          val t2 = a() to b()
          t1 + ": " + t2
        }
        assert(c() === "1 and 2: Range(1, 2)")
        a() = 0
        assert(c() === "0 and 2: Range(0, 1, 2)")
      }
      "use in by name parameters" in {
        val a = Var(1);

        val b = Rx{ Some(1).getOrElse(a()) }
        assert(b() === 1)
      }
    }
  }

  "obs tests" - {
    "obs Hello World" in {
      val a = Var(1)
      var s = 0
      val o = Obs(a){
        s = s + 1
      }
      a() = 2
      assert(s === 1)
    }
    "obs simple example" in {
      val a = Var(1)
      val b = Rx{ a() * 2 }
      val c = Rx{ a() + 1 }
      val d = Rx{ b() + c() }
      var bS = 0;     val bO = Obs(b){ bS += 1 }
      var cS = 0;     val cO = Obs(c){ cS += 1 }
      var dS = 0;     val dO = Obs(d){ dS += 1 }

      a() = 2

      assert(bS === 1);   assert(cS === 1);   assert(dS === 1)

      a() = 1

      assert(bS === 2);   assert(cS === 2);   assert(dS === 2)
    }
  }

  "error handling" - {
    "simple catch" in {
      val a = Var(1)
      val b = Rx{ 1 / a() }
      assert(b.toTry == Success(1))
      a() = 0
      inside(b.toTry){ case Failure(_) => true }
    }
    "long chain" in {
      val a = Var(1)

      val b = Var(2)

      val c = Rx{ a() / b() }
      val d = Rx{ a() * 5 }
      val e = Rx{ 5 / b() }
      val f = Rx{ a() + b() + 2 }
      val g = Rx{ f() + c() }

      inside(c.toTry){case Success(_) => true }
      inside(d.toTry){case Success(_) => true }
      inside(e.toTry){case Success(_) => true }
      inside(f.toTry){case Success(_) => true }
      inside(g.toTry){case Success(_) => true }

      b() = 0

      inside(c.toTry){case Failure(_) => true }
      inside(d.toTry){case Success(_) => true }
      inside(e.toTry){case Failure(_) => true }
      inside(f.toTry){case Success(_) => true }
      inside(g.toTry){case Failure(_) => true }
    }
  }
  "nested Rxs" - {
    val a = Var(1)
    val b = Rx{
      Rx{a()} -> Rx{math.random}
    }
    val r = b()._2()
    a() = 2
    assert(b()._2() === r)
  }


}