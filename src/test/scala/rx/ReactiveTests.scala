package rx
import org.scalatest._

class ReactiveTests extends FreeSpec{

  "All Tests" - {
    "sig tests" - {
      "basic" - {
        "signal Hello World" in {
          val a = Var(1); val b = Var(2)
          val c = Sig{ a() + b() }
          assert(c.now() === 3)
          a() = 4
          assert(c.now() === 6)

        }
        "long chain" in {
          val a = Var(1) // 3

          val b = Var(2) // 2

          val c = Sig{ a() + b() } // 5
          val d = Sig{ c() * 5 } // 25
          val e = Sig{ c() + 4 } // 9
          val f = Sig{ d() + e() + 4 } // 25 + 9 + 4 =

          assert(f.now() === 26)
          a() = 3
          assert(f.now() === 38)
        }
      }
      "language features" - {
        "pattern matching" in {
          val a = Var(1); val b = Var(2)
          val c = Sig{
            a() match{
              case 0 => b()
              case x => x
            }
          }
          assert(c.now() === 1)
          a() = 0
          assert(c.now() === 2)
        }
        "implicit conversions" in {
          /*val a = Var(1); val b = Var(2)
          val c = Sig{ implicit i =>
            val t1 = a() + " and " + b()
            val t2 = a() to b()
            t1 + t2
          }*/
        }
        "use in by name parameters" in {
//          val a = Var(1);
//
//          val c = Sig{
//
//            Some(1).getOrElse(a())
//          }
        }
      }
      "futures" -{


        "signal futures" in {
          val a = Var[Int]; val b = Var[Int]
          val c = Sig{ a() + b() }

          assert(!c.isCompleted)
          a() = 1
          assert(!c.isCompleted)
          b() = 2
          assert(c.now() === 3)
          assert(c.isCompleted)
          a() = 2
          assert(c.now() === 4)
          assert(c.isCompleted)
        }
        "partial execution with futures, checking CPS stages" in {
          val a = Var[Int]; val b = Var[Int]
          var s = 0
          val c = Sig{
            s = 1
            val first = a()
            s = 2
            val second = b()
            s = 3
            val third = 10
            first + second + third
          }

          assert(s === 1)
          assert(!c.isCompleted)
          a() = 5
          assert(s === 2)
          assert(!c.isCompleted)
          b() = 7
          assert(s === 3)
          assert(c.isCompleted)
          assert(c.now() === 22)
        }


        "partial execution with futures, ensuring single run through" in {
          var s = 0
          val a = Var[Int]; val b = Var[Int]
          val c = Sig{
            s += 1
            val first = a()
            s += 10
            val second = b()
            s += 100
            first + second + 10
          }

          assert(s === 1)
          assert(!c.isCompleted)

          a() = 5
          assert(s === 11)
          assert(!c.isCompleted)

          b() = 2
          assert(s === 111)
          assert(c.isCompleted)
          assert(c.now() === 17)
        }

        "partial execution with re-calcs halfway through" in {
          var s = 0
          val a = Var[Int]; val b = Var[Int]
          val c = Sig{
            s += 1
            val first = a()
            s += 10
            val second = b()
            s += 100
            first + second + 10
          }

          assert(s === 1)
          assert(!c.isCompleted)

          a() = 5
          assert(s === 11)
          assert(!c.isCompleted)

          a() = 2
          assert(s === 22)
          assert(!c.isCompleted)

          b() = 3
          assert(s === 122)
          assert(c.isCompleted)

          assert(c.now() === 15)
        }

        "ensuring low-level blocked signals dont prevent other higher-level signals from proceeding" in {

          val a = Var(1); val b = Var[Int]

          val c = Sig{ a() * 2 }
          val d = Sig{ c() * 2 }
          val e = Sig{ a() + b() * 2 }

          assert(c.now() === 2)
          assert(d.now() === 4)
          assert(!e.isCompleted)

          a() = 2
          assert(c.now() === 4)
          assert(d.now() === 8)
          assert(!e.isCompleted)

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
        a() = 1
        assert(s === 1)
      }
      "obs simple example" in {
        val a = Var(1)
        val b = Sig{ a() * 2 }
        val c = Sig{ a() + 1 }
        val d = Sig{ b() + c() }
        var bS = 0;     val bO = Obs(b){ bS += 1 }
        var cS = 0;     val cO = Obs(c){ cS += 1 }
        var dS = 0;     val dO = Obs(d){ dS += 1 }
        println("\n1")
        a() = 2
        println("1\n")
        assert(bS === 1);   assert(cS === 1);   assert(dS === 1)
        println("\n2")
        a() = 1
        println("2\n")
        assert(bS === 2);   assert(cS === 2);   assert(dS === 2)
      }
    }
  }
}
