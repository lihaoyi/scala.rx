package rx.opmacros

import rx.{Rx, RxCtx}

import scala.language.experimental.macros
import scala.reflect.macros._
/**
  * Created by haoyi on 1/18/16.
  */
object Utils {
  /**
    * Walks a tree and injects in an implicit `RxCtx` over any `RxCtx` that
    * was previously inferred. This is done because by the time the macro runs,
    * implicits have already been resolved, so we cannot rely on implicit
    * resolution to do this for us
    */
  def injectRxCtx[T](c: Context)(src: c.Tree, newCtx: c.universe.TermName, curCtxTree: c.Tree): c.Tree = {
    import c.universe._
    object transformer extends c.universe.Transformer {
      override def transform(tree: c.Tree): c.Tree = {
        if (curCtxTree.isEmpty) q"$newCtx"
        else if (tree.tpe =:= c.weakTypeOf[RxCtx.CompileTime.type]) q"$newCtx"
        else if (tree.equalsStructure(curCtxTree)) q"$newCtx"
        else if (tree.tpe =:= c.weakTypeOf[RxCtx.Unsafe.type]) q"$newCtx"
        else super.transform(tree)
      }
    }
    transformer.transform(src)
  }

  def ensureStaticEnclosingOwners(c: Context)(chk: c.Symbol, abortOnFail: Boolean): Boolean = {
    import c.universe._
    //Failed due to an enclosing trait or abstract class
    if(chk.isAbstract && chk.isClass) {
      val msg = s"This Rx might leak! Either explicitly mark it unsafe (Rx.unsafe) or ensure an implicit RxCtx is in scope!"
      if(abortOnFail) c.abort(c.enclosingPosition,msg)
      else false
    }
    //Failed due to an enclosing method or class
    else if((chk.isMethod && !(chk.isMethod && chk.isTerm && chk.asTerm.isLazy)) || (chk.isClass && !chk.isModuleClass)) {
      val msg =s"""
                  |This Rx might leak! Either explicitly mark it unsafe (Rx.unsafe) or make an implicit RxCtx available
                  |in the enclosing scope, for example, by adding (implicit ctx: RxCtx) to line ${chk.pos.line}: $chk
                  |""".stripMargin
      if(abortOnFail) c.abort(c.enclosingPosition, msg)
      else false
    }
    else if(chk.owner == NoSymbol) true
    else ensureStaticEnclosingOwners(c)(chk.owner, abortOnFail)
  }

  def buildSafeCtx(c: Context)(): c.Expr[RxCtx] = {
    import c.universe._

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[RxCtx], withMacrosDisabled = true)

    val isCompileTimeCtx = inferredCtx.isEmpty || inferredCtx.tpe =:= c.weakTypeOf[RxCtx.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val safeCtx =
      if(isCompileTimeCtx) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else if(c.internal.enclosingOwner.fullName == inferredCtx.symbol.fullName) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else c.Expr[RxCtx](q"$inferredCtx")

    safeCtx
  }

  def encCtx(c: Context)(ctx: c.Expr[RxCtx]) = {
    import c.universe._
    val isCompileTimeCtx = ctx.tree.tpe =:= c.weakTypeOf[RxCtx.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else ctx

    enclosingCtx
  }

  def buildMacro[T: c.WeakTypeTag](c: Context)(func: c.Expr[T])(curCtx: c.Expr[RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._

    val newCtx =  c.fresh[TermName]("rxctx")

    val isCompileTimeCtx = curCtx.tree.tpe =:= c.weakTypeOf[RxCtx.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else curCtx

    val res = q"rx.Rx.build{$newCtx: rx.RxCtx => ${injectRxCtx(c)(func.tree, newCtx, curCtx.tree)}}($enclosingCtx)"
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def buildUnsafe[T: c.WeakTypeTag](c: Context)(func: c.Expr[T]): c.Expr[Rx[T]] = {
    import c.universe._

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[RxCtx])

    require(!inferredCtx.isEmpty)

    val newCtx = c.fresh[TermName]("rxctx")

    val enclosingCtx =
      if(inferredCtx.tpe =:= c.weakTypeOf[RxCtx.CompileTime.type]) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else if(inferredCtx.isEmpty) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else c.Expr[RxCtx](q"$inferredCtx")

    val res = q"rx.Rx.build{$newCtx: rx.RxCtx => ${injectRxCtx(c)(func.tree, newCtx, inferredCtx)}}($enclosingCtx)"
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def buildImplicitRxCtx(c: Context): c.Expr[RxCtx] = {
    import c.universe._
    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[RxCtx], withMacrosDisabled = true)
    val isCompileTime = inferredCtx.isEmpty
    val staticContext = ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = false)
    val implicitCtx =
      if(isCompileTime && staticContext) q"rx.RxCtx.Unsafe"
      else if(isCompileTime && !staticContext) q"rx.RxCtx.CompileTime"
      else q"$inferredCtx"
    c.Expr[RxCtx](c.resetLocalAttrs(implicitCtx))
  }
}
