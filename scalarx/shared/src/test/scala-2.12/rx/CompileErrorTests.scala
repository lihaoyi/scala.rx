package rx
import utest._
object CompileErrorTests extends TestSuite{
  val tests = TestSuite{
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
        compileError("""compileError("object Fail { def fail() = { implicit val ctx = Ctx.Owner.Unsafe ; Rx { } } }")""")
      }
    }
    'separateOwnerData - {
      // This test doesn't work because `compileError` isn't smart enough
      // to detect failures due to @compileTimeOnly

      //      compileError("""
      //        def foo()(implicit ctx: rx.Ctx.Owner) = {
      //          val a = rx.Var(1)
      //          val b = rx.Rx(a() + 1)
      //          println(b())
      //          a
      //        }
      //
      //
      //        val x = rx.Rx.unsafe{
      //          val y = foo(); y() = y() + 1; println("done!")
      //        }
      //      """)
    }
    'noTopLevelApply - {
      // This one fails to have a compileError too for the same reason
      //      compileError("""
      //        object foo{
      //          val a = Var(1)
      //          a()
      //        }
      //      """)
    }
  }
}