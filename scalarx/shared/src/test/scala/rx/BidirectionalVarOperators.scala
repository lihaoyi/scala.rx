package rx

import acyclic.file
import monocle.macros.GenLens
import utest._

object TransformedVarTests extends TestSuite {
  def tests = utest.Tests {
    "isomorphic Var" - {
      import Ctx.Owner.Unsafe._

      val a: Var[Int] = Var(0)
      val b: Var[String] = a.imap(_.toString)(_.toInt)
      val c: Rx[String] = b.map(_ + "q")

      assert(a.now == 0)
      assert(a.value == 0)
      assert(b.now == "0")
      assert(b.value == "0")
      assert(c.now == "0q")

      a() = 1
      assert(a.now == 1)
      assert(a.value == 1)
      assert(b.now == "1")
      assert(b.value == "1")
      assert(c.now == "1q")

      b() = "2"
      assert(a.now == 2)
      assert(a.value == 2)
      assert(b.now == "2")
      assert(b.value == "2")
      assert(c.now == "2q")

      a() = 3
      assert(a.now == 3)
      assert(a.value == 3)
      assert(b.now == "3")
      assert(b.value == "3")
      assert(c.now == "3q")

      b() = "2"
      assert(a.now == 2)
      assert(a.value == 2)
      assert(b.now == "2")
      assert(b.value == "2")
      assert(c.now == "2q")
    }

    "zoomedVar" - {
      import Ctx.Owner.Unsafe._

      val a: Var[(Int, String)] = Var((0, "Wurst"))
      val b: Var[String] = a.zoom(_._2)((a, b) => a.copy(_2 = b))
      val c: Rx[String] = b.map(_ + "q")

      assert(a.now == ((0, "Wurst")))
      assert(a.value == ((0, "Wurst")))
      assert(b.now == "Wurst")
      assert(b.value == "Wurst")
      assert(c.now == "Wurstq")

      a() = (1, "hoho")
      assert(a.now == ((1, "hoho")))
      assert(a.value == ((1, "hoho")))
      assert(b.now == "hoho")
      assert(b.value == "hoho")
      assert(c.now == "hohoq")

      b() = "Voodoo"
      assert(a.now == ((1, "Voodoo")))
      assert(a.value == ((1, "Voodoo")))
      assert(b.now == "Voodoo")
      assert(b.value == "Voodoo")
      assert(c.now == "Voodooq")

      a() = (3, "genau")
      assert(a.now == ((3, "genau")))
      assert(a.value == ((3, "genau")))
      assert(b.now == "genau")
      assert(b.value == "genau")
      assert(c.now == "genauq")

      b() = "Schwein"
      assert(a.now == ((3, "Schwein")))
      assert(a.value == ((3, "Schwein")))
      assert(b.now == "Schwein")
      assert(b.value == "Schwein")
      assert(c.now == "Schweinq")
    }

    "zoomed Var with Monocle Lens" - {
      import Ctx.Owner.Unsafe._

      case class Company(name: String, zipcode: Int)
      case class Employee(name: String, company: Company)

      val employee = Var(Employee("jules", Company("wules", 7)))
      val zipcode = employee.zoom(GenLens[Employee](_.company.zipcode))

      assert(employee.now == Employee("jules", Company("wules", 7)))
      assert(zipcode.now == 7)

      zipcode() = 8
      assert(employee.now == Employee("jules", Company("wules", 8)))
      assert(zipcode.now == 8)

      employee() = Employee("gula", Company("bori", 6))
      assert(employee.now == Employee("gula", Company("bori", 6)))
      assert(zipcode.now == 6)
    }

    "isomorphic Var function calls" - {
      import Ctx.Owner.Unsafe._

      var readCount = 0
      val read: Int => String = { a => readCount += 1; a.toString }

      var writeCount = 0
      val write: String => Int = { b => writeCount += 1; b.toInt }

      val a: Var[Int] = Var(7)
      val b: Var[String] = a.imap(read)(write)

      assert(readCount == 1)
      assert(writeCount == 0)

      a() = 1
      assert(readCount == 2)
      assert(writeCount == 0)

      b() = "5"
      assert(readCount == 2)
      assert(writeCount == 1)

      a() = 3
      assert(readCount == 3)
      assert(writeCount == 1)
    }

    "zoomed Var function calls" - {
      import Ctx.Owner.Unsafe._

      var readCount = 0
      val read: ((Int, String)) => String = { a => readCount += 1; a._2 }

      var writeCount = 0
      val write: ((Int, String), String) => (Int, String) = { (a, b) => writeCount += 1; a.copy(_2 = b) }

      val a: Var[(Int, String)] = Var((0, "Wurst"))
      val b: Var[String] = a.zoom(read)(write)

      assert(readCount == 1)
      assert(writeCount == 0)

      a() = (1, "hoho")
      assert(readCount == 2)
      assert(writeCount == 0)

      b() = "Voodoo"
      assert(readCount == 2)
      assert(writeCount == 1)

      a() = (4, "gurkola")
      assert(readCount == 3)
      assert(writeCount == 1)
    }


    "Composed Var" - {
      import Ctx.Owner.Unsafe._

      val list = Var(List(1, 2, 3))
      val selectedItem = {
        val rawSelected = Var[Option[Int]](Some(1))
        new Var.Composed(rawSelected, Rx {
          rawSelected().filter(list() contains _)
        })
      }

      assert(selectedItem.now == Some(1))

      selectedItem() = Some(4)
      assert(selectedItem.now == None)

      list.update(4 :: _)
      assert(selectedItem.now == Some(4))
    }

    "mapRead" - {
      import Ctx.Owner.Unsafe._

      val list = Var(List(1, 2, 3))
      val selectedItem = Var[Option[Int]](Some(1)).mapRead {
        selected => selected().filter(list() contains _)
      }

      assert(selectedItem.now == Some(1))

      selectedItem() = Some(4)
      assert(selectedItem.now == None)

      list.update(4 :: _)
      assert(selectedItem.now == Some(4))
    }

    "multisetZoomedVar" - {
      import Ctx.Owner.Unsafe._

      val x:Var[(Int,String)] = Var((0,"Wurst"))
      val y = x.map(_.toString)
      val a:Var[Int] = x.zoom(_._1)((x, a) => x.copy(_1 = a))
      val b:Var[String] = x.zoom(_._2)((x, b) => x.copy(_2 = b))

      var ix = 0; x.trigger { ix += 1 }
      var iy = 0; y.trigger { iy += 1 }
      var ia = 0; a.trigger { ia += 1 }
      var ib = 0; b.trigger { ib += 1 }

      assert(ix == 1)
      assert(iy == 1)
      assert(ia == 1)
      assert(ib == 1)

      Var.set(
        a -> 7,
        b -> "Kartoffel"
      )
      assert(x.now == ((7,"Kartoffel")))
      assert(y.now == "(7,Kartoffel)")
      assert(ix == 2)
      assert(iy == 2)
      assert(ia == 2)
      assert(ib == 2)
    }
  }
}

