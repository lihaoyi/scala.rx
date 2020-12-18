package rx

import acyclic.file
import monocle.macros.GenLens
import utest._

object BidirectionalVarOperatorTests extends TestSuite {
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

    "zoomed Var chained" - {
      import Ctx.Owner.Unsafe._

      case class Company(name: String, zipcode: Int)
      case class Employee(name: String, company: Company)

      val employee = Var(Employee("jules", Company("wules", 7)))
      val company = employee.zoom(_.company)((old, value) => old.copy(company = value))
      val zipcode = company.zoom(_.zipcode)((old, value) => old.copy(zipcode = value))

      assert(company.now == Company("wules", 7))
      assert(employee.now == Employee("jules", Company("wules", 7)))
      assert(zipcode.now == 7)

      zipcode() = 8
      assert(company.now == Company("wules", 8))
      assert(employee.now == Employee("jules", Company("wules", 8)))
      assert(zipcode.now == 8)

      employee() = Employee("gula", Company("bori", 6))
      assert(company.now == Company("bori", 6))
      assert(employee.now == Employee("gula", Company("bori", 6)))
      assert(zipcode.now == 6)

      company() = Company("borislav", 13)
      assert(company.now == Company("borislav", 13))
      assert(employee.now == Employee("gula", Company("borislav", 13)))
      assert(zipcode.now == 13)
    }

    "zoomed Var chained with observer" - {
      import Ctx.Owner.Unsafe._

      case class Company(name: String, zipcode: Int)
      case class Employee(name: String, company: Company)

      val employee = Var(Employee("jules", Company("wules", 7)))
      val company = employee.zoom(_.company)((old, value) => old.copy(company = value))
      val zipcode = company.zoom(_.zipcode)((old, value) => old.copy(zipcode = value))

      var employeeNow = employee.now
      employee.foreach { employeeNow = _ }

      assert(company.now == Company("wules", 7))
      assert(employeeNow == Employee("jules", Company("wules", 7)))
      assert(employee.now == Employee("jules", Company("wules", 7)))
      assert(zipcode.now == 7)

      zipcode() = 8
      assert(company.now == Company("wules", 8))
      assert(employeeNow == Employee("jules", Company("wules", 8)))
      assert(employee.now == Employee("jules", Company("wules", 8)))
      assert(zipcode.now == 8)

      employee() = Employee("gula", Company("bori", 6))
      assert(company.now == Company("bori", 6))
      assert(employeeNow == Employee("gula", Company("bori", 6)))
      assert(employee.now == Employee("gula", Company("bori", 6)))
      assert(zipcode.now == 6)

      company() = Company("borislav", 13)
      assert(company.now == Company("borislav", 13))
      assert(employeeNow == Employee("gula", Company("borislav", 13)))
      assert(employee.now == Employee("gula", Company("borislav", 13)))
      assert(zipcode.now == 13)
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

    "mapRead observer" - {
      import Ctx.Owner.Unsafe._

      val list = Var(List(1, 2, 3))
      val selectedItem = Var[Option[Int]](Some(1)).mapRead {
        selected => selected().filter(list() contains _)
      }
      var myNow: Option[Int] = None
      selectedItem.foreach { myNow = _ }

      assert(myNow == Some(1))

      selectedItem() = Some(4)
      assert(myNow == None)

      list.update(4 :: _)
      assert(myNow == Some(4))
    }

    "mapRead then zoom (write into outer)" - {
      import Ctx.Owner.Unsafe._
      case class SelectedWrapper(selected:Option[Int])

      val list = Var(List(1, 2, 3))
      val selWrapper = Var[SelectedWrapper](SelectedWrapper(Some(1))).mapRead {
        selWrapper => selWrapper().copy(selected = selWrapper().selected.filter(list() contains _))
      }
      val selectedItem = selWrapper.zoom(_.selected)((selWrapper,selected) => selWrapper.copy(selected = selected))

      assert(selWrapper.now.selected == Some(1))
      assert(selectedItem.now == Some(1))

      selWrapper() = SelectedWrapper(Some(4))
      assert(selWrapper.now.selected == None)
      assert(selectedItem.now == None) // fail: is Some(3) ...

      list.update(4 :: _)
      assert(selWrapper.now.selected == Some(4))
      assert(selectedItem.now == Some(4)) // fail: still is Some(3)

      selWrapper() = SelectedWrapper(Some(4))
      assert(selWrapper.now.selected == Some(4))
      assert(selectedItem.now == Some(4))
    }

    "mapRead then zoom (write into zoomed)" - {
      import Ctx.Owner.Unsafe._
      case class SelectedWrapper(selected:Option[Int])

      val list = Var(List(1, 2, 3))
      val selWrapper = Var[SelectedWrapper](SelectedWrapper(Some(1)))
      val selWrapperFiltered = selWrapper.mapRead {
        selWrapper => selWrapper().copy(selected = selWrapper().selected.filter(list() contains _))
      }
      val selectedItem = selWrapperFiltered.zoom(_.selected)((selWrapper,selected) => selWrapper.copy(selected = selected))

      assert(selWrapper.now.selected == Some(1))
      assert(selWrapperFiltered.now.selected == Some(1))
      assert(selectedItem.now == Some(1))

      selectedItem() = Some(4) // write indirectly into selWrapper
      assert(selWrapper.now.selected == Some(4))
      assert(selWrapperFiltered.now.selected == None)
      assert(selectedItem.now == None) // fail: is Some(3) ...

      list.update(4 :: _)
      assert(selWrapper.now.selected == Some(4))
      assert(selWrapperFiltered.now.selected == Some(4))
      assert(selectedItem.now == Some(4)) // fail: still is Some(3)

      selectedItem() = Some(4)
      assert(selWrapper.now.selected == Some(4))
      assert(selWrapperFiltered.now.selected == Some(4))
      assert(selectedItem.now == Some(4))
    }

    "imap then zoom (write into zoomed)" - {
      import Ctx.Owner.Unsafe._
      case class Wrapper(x:Int)
      val base = Var(Wrapper(5))
      val imapped = base.imap(w => w.copy(x = w.x + 1))(w =>  w.copy(x = w.x - 1))
      val zoomed = imapped.zoom(_.x)( (old, value) => old.copy(x = value))

      zoomed() = 1
      assert(zoomed.now == 1)
      assert(imapped.now == Wrapper(1))
      assert(base.now == Wrapper(0))

      zoomed() = 2
      assert(zoomed.now == 2)
      assert(imapped.now == Wrapper(2))
      assert(base.now == Wrapper(1))
    }

    "mapRead function calls" - {
      import Ctx.Owner.Unsafe._

      var aReadCounter = 0
      var bReadCounter = 0
      var cReadCounter = 0
      val a: Var[Int] = Var(0)
      val nested: Var[Int] = Var(1)
      val b: Var[Int] = a.mapRead { v => v() + nested() }
      val c: Rx[Int] = b.map(_ + 1)
      a.foreach { _ => aReadCounter += 1 }
      b.foreach { _ => bReadCounter += 1 }
      c.foreach { _ => cReadCounter += 1 }

      assert(aReadCounter == 1)
      assert(bReadCounter == 1)
      assert(cReadCounter == 1)
      assert(a.now == 0)
      assert(b.now == 1)
      assert(c.now == 2)

      a() = 1
      assert(aReadCounter == 2)
      assert(bReadCounter == 2)
      assert(cReadCounter == 2)
      assert(a.now == 1)
      assert(b.now == 2)
      assert(c.now == 3)

      b() = 2
      assert(aReadCounter == 3)
      assert(bReadCounter == 3)
      assert(cReadCounter == 3)
      assert(a.now == 2)
      assert(b.now == 3)
      assert(c.now == 4)

      a() = 3
      assert(aReadCounter == 4)
      assert(bReadCounter == 4)
      assert(cReadCounter == 4)
      assert(a.now == 3)
      assert(b.now == 4)
      assert(c.now == 5)

      nested() = 10
      assert(aReadCounter == 4)
      assert(bReadCounter == 5)
      assert(cReadCounter == 5)
      assert(a.now == 3)
      assert(b.now == 13)
      assert(c.now == 14)
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

    "multiple dependencies with Composed" - {
      import Ctx.Owner.Unsafe._
      for( _ <- 0 to 100) { // catch nondeterminism
        val base = Var(0)
        val num = new Var.Composed(base, Rx { base() + 1 })
        val combined = Rx {
          base()
          num()
        }

        assert(base.now == 0)
        assert(num.now == 1)
        assert(combined.now == 1)

        num() = 1

        assert(base.now == 1)
        assert(num.now == 2)
        assert(combined.now == 2)

        base() = 1

        assert(base.now == 1)
        assert(num.now == 2)
        assert(combined.now == 2)
      }
    }

    "multiple dependencies with mapRead" - {
      import Ctx.Owner.Unsafe._
      for( _ <- 0 to 100) { // catch nondeterminism
        val base = Var(0)
        val num = base.mapRead { num => num() + 1 }
        val combined = Rx {
          base()
          num()
        }

        assert(base.now == 0)
        assert(num.now == 1)
        assert(combined.now == 1)

        num() = 1

        assert(base.now == 1)
        assert(num.now == 2)
        assert(combined.now == 2)

        base() = 1

        assert(base.now == 1)
        assert(num.now == 2)
        assert(combined.now == 2)
      }
    }

    "multiple dependencies with zoomed" - {
      import Ctx.Owner.Unsafe._
      for( _ <- 0 to 100) { // catch nondeterminism
        val base: Var[(Int, String)] = Var((0, "Wurst"))
        val num: Var[Int] = base.zoom(x => x._1 + 1)((a, b) => a.copy(_1 = b))
        val combined = Rx {
          base()
          num()
        }

        assert(base.now == (0 -> "Wurst"))
        assert(num.now == 1)
        assert(combined.now == 1)

        num() = 1

        assert(base.now == (1 -> "Wurst"))
        assert(num.now == 1)
        assert(combined.now == 1)

        base() = (1, "Käse")

        assert(base.now == (1 -> "Käse"))
        assert(num.now == 2)
        assert(combined.now == 2)
      }
    }

    "multiple dependencies with isomorphic" - {
      import Ctx.Owner.Unsafe._
      for( _ <- 0 to 100) { // catch nondeterminism
        val base = Var(0)
        val num = base.imap(_ + 1)(_ - 1)
        val combined = Rx {
          base()
          num()
        }

        assert(base.now == 0)
        assert(num.now == 1)
        assert(combined.now == 1)

        num() = 1

        assert(base.now == 0)
        assert(num.now == 1)
        assert(combined.now == 1)

        base() = 1

        assert(base.now == 1)
        assert(num.now == 2)
        assert(combined.now == 2)
      }
    }
  }
}

