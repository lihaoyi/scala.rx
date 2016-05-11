package rx.opmacros

import rx.Rx
import rx.opmacros.Utils._

import scala.language.experimental.macros
import scala.reflect.macros._

/**
  * Macros used to
  */
object Factories {

  def buildSafeCtx[T: c.WeakTypeTag](c: Context)(): c.Expr[T] = {
    import c.universe._

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Owner], withMacrosDisabled = true)

    val isCompileTimeCtx = inferredCtx.isEmpty || inferredCtx.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      Utils.ensureStaticEnclosingOwners(c)(rx.opmacros.Compat.enclosingName(c), abortOnFail = true)

    val safeCtx =
      if(isCompileTimeCtx) q"_root_.rx.Ctx.Owner.Unsafe"
      else if(rx.opmacros.Compat.enclosingName(c).fullName == inferredCtx.symbol.fullName) q"_root_.rx.Ctx.Owner.Unsafe"
      else q"$inferredCtx"

    c.Expr[T](safeCtx)
  }

  def rxApplyMacro[T: c.WeakTypeTag]
                  (c: Context)
                  (func: c.Expr[T])
                  (ownerCtx: c.Expr[rx.Ctx.Owner], name: c.Expr[sourcecode.Name])
                  : c.Expr[Rx.Dynamic[T]] = {
    import c.universe._

    val dataCtx = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Data])
    val newDataCtx =  c.fresh[TermName]("rxDataCtx")
    val newOwnerCtx =  c.fresh[TermName]("rxOwnerCtx")

    val isCompileTimeCtx = ownerCtx.tree.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      Utils.ensureStaticEnclosingOwners(c)(rx.opmacros.Compat.enclosingName(c), abortOnFail = true)

    val injected2 = Utils.doubleInject(c)(func.tree, newOwnerCtx, ownerCtx.tree, newDataCtx, dataCtx)

    resetExpr[Rx.Dynamic[T]](c)(q"""_root_.rx.Rx.build{
      ($newOwnerCtx: _root_.rx.Ctx.Owner, $newDataCtx: _root_.rx.Ctx.Data) => $injected2
    }""")
  }

  def buildUnsafe[T: c.WeakTypeTag](c: Context)(func: c.Expr[T])(name: c.Expr[sourcecode.Name]): c.Expr[Rx[T]] = {
    import c.universe._

    val inferredOwner = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Owner])
    val inferredData = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Data])

    require(!inferredOwner.isEmpty)

    val newDataCtx =  c.fresh[TermName]("rxDataCtx")
    val newOwnerCtx =  c.fresh[TermName]("rxOwnerCtx")

    val unsafeOwner =
      if(inferredOwner.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type])
        q"_root_.rx.Ctx.Owner.Unsafe"
      else if(inferredOwner.isEmpty)
        q"_root_.rx.Ctx.Owner.Unsafe"
      else
        inferredOwner

    val injected2 = Utils.doubleInject(c)(func.tree, newOwnerCtx, inferredOwner, newDataCtx, inferredData)

    resetExpr[Rx[T]](c)(q"""_root_.rx.Rx.build{
      ($newOwnerCtx: _root_.rx.Ctx.Owner, $newDataCtx: _root_.rx.Ctx.Data) => $injected2
    }($unsafeOwner,$name)""")
  }

  def automaticOwnerContext[T: c.WeakTypeTag](c: Context): c.Expr[T] = {
    import c.universe._
    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[T], withMacrosDisabled = true)
    val isCompileTime = inferredCtx.isEmpty
    val staticContext = Utils.ensureStaticEnclosingOwners(c)(rx.opmacros.Compat.enclosingName(c), abortOnFail = false)
    val implicitCtx =
      if(isCompileTime && staticContext) q"${c.prefix}.Unsafe"
      else if(isCompileTime && !staticContext) q"${c.prefix}.CompileTime"
      else q"$inferredCtx"

    resetExpr[T](c)(implicitCtx)
  }
}
