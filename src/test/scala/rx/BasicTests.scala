package rx
import org.scalatest._
import util.{Failure, Success}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}

class BasicTests extends FreeSpec with Inside with Eventually{
  implicit val patience = (PatienceConfig(Span(10000, Millis)))
  implicit val prop = Propagator.Immediate
  "config tests" - {
    "name" in {
      val v1 = Var(0)
      val v2 = Var(0, name = "v2")

      val s1 = Rx{v1() + 1}

      val s2 = Rx(v2() + 1, name = "s2")

      val o1 = Obs(s1){ }
      val o2 = Obs(s2, name = "o2"){ }

      assert(v1.name === "")
      assert(v2.name === "v2")

      assert(s1.name === "")
      assert(s2.name === "s2")

      assert(o1.name === "")
      assert(o2.name === "o2")
    }
  }

  "sig tests" - {
    "basic" - {
      "Rx Hello World" in {
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

      "complex values inside Var[]s and Rx[]s" in {
        val a = Var(Seq(1, 2, 3))
        val b = Var(3)
        val c = Rx{ b() +: a() }
        val d = Rx{ c().map("omg" * _) }
        val e = Var("wtf")
        val f = Rx{ (d() :+ e()).mkString }

        assert(f() === "omgomgomgomgomgomgomgomgomgwtf")
        a() = Nil
        assert(f() === "omgomgomgwtf")
        e() = "wtfbbq"
        assert(f() === "omgomgomgwtfbbq")

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
      var count = 0
      val o = Obs(a){
        count = a() + 1
      }
      assert(count === 2)
      a() = 4
      assert(count === 5)
    }
    "obs skipInitial" in {
      val a = Var(1)
      var count = 0
      val o = Obs(a, skipInitial=true){
        count = count + 1
      }
      assert(count === 0)
      a() = 2
      assert(count === 1)
    }

    "obs simple example" in {
      val a = Var(1)
      val b = Rx{ a() * 2 }
      val c = Rx{ a() + 1 }
      val d = Rx{ b() + c() }
      var bS = 0;     val bO = Obs(b){ bS += 1 }
      var cS = 0;     val cO = Obs(c){ cS += 1 }
      var dS = 0;     val dO = Obs(d){ dS += 1 }

      assert(bS === 1);   assert(cS === 1);   assert(dS === 1)

      a() = 2

      assert(bS === 2);   assert(cS === 2);   assert(dS === 2)

      a() = 1

      assert(bS === 3);   assert(cS === 3);   assert(dS === 3)
    }

    "obs getting GCed" in {
      val a = Var(1)
      var count = 0
      Obs(a){
        count = count + 1
      }
      assert(count === 1)
      eventually{
        val oldCount = count
        a() = a() + 1
        System.gc()
        assert(oldCount === count)
      }
    }

  }

  "error handling" - {
    "simple catch" in {
      val a = Var(1)
      val b = Rx{ 1 / a() }
      assert(b() === 1)
      assert(b.toTry === Success(1))
      a() = 0
      intercept[Exception]{
        b()
      }
      inside(b.toTry){ case Failure(_) => () }
    }
    "long chain" in {
      val a = Var(1)
      val b = Var(2)

      val c = Rx{ a() / b() }
      val d = Rx{ a() * 5 }
      val e = Rx{ 5 / b() }
      val f = Rx{ a() + b() + 2 }
      val g = Rx{ f() + c() }

      inside(c.toTry){case Success(0) => () }
      inside(d.toTry){case Success(5) => () }
      inside(e.toTry){case Success(2) => () }
      inside(f.toTry){case Success(5) => () }
      inside(g.toTry){case Success(5) => () }

      b() = 0

      inside(c.toTry){case Failure(_) => () }
      inside(d.toTry){case Success(5) => () }
      inside(e.toTry){case Failure(_) => () }
      inside(f.toTry){case Success(3) => () }
      inside(g.toTry){case Failure(_) => () }
    }
  }


}