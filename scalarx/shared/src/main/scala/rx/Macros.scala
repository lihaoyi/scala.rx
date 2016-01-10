package rx

import scala.language.experimental.macros
import scala.reflect.macros._

object Macros {

  def transformFunc[T](c: Context)(func: c.Expr[T], newCtx: c.universe.TermName, curCtxTree: c.Tree): c.Tree = {
    import c.universe._
    object transformer extends c.universe.Transformer {
      override def transform(tree: c.Tree): c.Tree = {
        if (tree.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]) q"$newCtx"
        else if (tree.tpe =:= c.weakTypeOf[rx.RxCtx.Unsafe.type]) q"$newCtx"
        else if (tree.equalsStructure(curCtxTree)) q"$newCtx"
        else super.transform(tree)
      }
    }
    transformer.transform(func.tree)
  }

  def ensureStaticEnclosingOwners(c: Context)(chk: c.Symbol): Unit = {
    import c.universe._
    //Failed due to an enclosing method or class
    if(chk.isMethod || (chk.isClass && !chk.isModuleClass)) {
      val msg =s"""
        |This Rx might leak! Either explicitly mark it unsafe (Rx.unsafe) or make an implicit RxCtx available
        |in the enclosing scope, for example, by adding (implicit ctx: RxCtx) to line ${chk.pos.line}: $chk
        |""".stripMargin
      c.abort(c.enclosingPosition, msg)
    }
    else if(chk.owner == NoSymbol) ()
    else ensureStaticEnclosingOwners(c)(chk.owner)
  }

  def buildMacro[T: c.WeakTypeTag](c: Context)(func: c.Expr[T])(curCtx: c.Expr[rx.RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._

    val newCtx =  c.fresh[TermName]("rxctx")

    val isCompileTimeCtx = curCtx.tree.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner)

    val enclosingCtx =
      if(isCompileTimeCtx) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else curCtx

    val res = q"rx.Rx.build{$newCtx: rx.RxCtx => ${transformFunc(c)(func, newCtx, curCtx.tree)}}($enclosingCtx)"
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def buildUnsafe[T: c.WeakTypeTag](c: Context)(func: c.Expr[T]): c.Expr[Rx[T]] = {
    import c.universe._

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[rx.RxCtx])

    val newCtx = c.fresh[TermName]("rxctx")

    val enclosingCtx =
      if(inferredCtx.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else c.Expr[RxCtx](q"$inferredCtx")

    val res = q"rx.Rx.build{$newCtx: rx.RxCtx => ${transformFunc(c)(func, newCtx, inferredCtx)}}($enclosingCtx)"
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }
}
