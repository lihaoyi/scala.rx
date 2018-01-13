package rx.opmacros

import rx.Rx

import scala.language.experimental.macros
import scala.reflect.macros._
/**
  * Helper functions for the other macros in this package.
  */
object Utils {
  /**
    * Walks a tree and injects in an implicit `Ctx.Owner` over any `Ctx.Owner` that
    * was previously inferred. This is done because by the time the macro runs,
    * implicits have already been resolved, so we cannot rely on implicit
    * resolution to do this for us
    */
  def injectRxCtx[T](c: blackbox.Context)
                    (src: c.Tree,
                     newCtx: c.universe.TermName,
                     curCtxTree: c.Tree,
                     compileTime: c.Type,
                     unsafe: Option[c.Type])
                    : c.Tree = {
    import c.universe._
    object transformer extends c.universe.Transformer {
      override def transform(tree: c.Tree): c.Tree = {
        if (curCtxTree.isEmpty) q"$newCtx"
        // this can happen because we're transforming the trees twice in a row,
        // and any trees injected by the first transform will be un-typed
        // during the second
        else if (tree.tpe == null) tree
        else if (tree.tpe =:= compileTime) q"$newCtx"
        else if (tree.equalsStructure(curCtxTree)) q"$newCtx"
        else if (unsafe.exists(tree.tpe =:= _)) q"$newCtx"
        else super.transform(tree)
      }
    }
    transformer.transform(src)
  }

  /**
    * Injects both the Owner and Data contexts into the given snippet
    */
  def doubleInject(c: blackbox.Context)
                  (src: c.Tree,
                   newOwner: c.TermName,
                   owner: c.Tree,
                   newData: c.TermName,
                   data: c.Tree) = {
    val newFunc = injectRxCtx(c)(
      src,
      newOwner,
      owner,
      c.weakTypeOf[rx.Ctx.Owner.CompileTime.type],
      Some(c.weakTypeOf[rx.Ctx.Owner.Unsafe.type])
    )
    val newFunc2 = injectRxCtx(c)(
      newFunc,
      newData,
      data,
      c.weakTypeOf[rx.Ctx.Data.CompileTime.type],
      None
    )
    newFunc2
  }
  def ensureStaticEnclosingOwners(c: blackbox.Context)(chk: c.Symbol, abortOnFail: Boolean): Boolean = {
    import c.universe._
    //Failed due to an enclosing trait or abstract class
    if(!chk.isModuleClass && chk.isClass) {
      val msg = s"This Rx might leak! Either explicitly mark it unsafe (Rx.unsafe) or ensure an implicit RxCtx is in scope!"
      if(abortOnFail) c.abort(c.enclosingPosition,msg)
      else false
    }
    //Failed due to an enclosing method or class
    else if((chk.isMethod && !(chk.isMethod && chk.isTerm && chk.asTerm.isLazy)) || (chk.isClass && !chk.isModuleClass)) {
      val msg =s"""
                  |This Rx might leak! Either explicitly mark it unsafe (Rx.unsafe) or make an implicit RxCtx available
                  |in the enclosing scope, for example, by adding (implicit ctx: Ctx.Owner) to line ${chk.pos.line}: $chk
                  |""".stripMargin
      if(abortOnFail) c.abort(c.enclosingPosition, msg)
      else false
    }
    else if(chk.owner == NoSymbol) {
      true
    }
    else {
      ensureStaticEnclosingOwners(c)(chk.owner, abortOnFail)
    }
  }


  def enclosingCtx(c: blackbox.Context)(ctx: c.Tree): c.Tree = {
    import c.universe._
    val isCompileTimeCtx = ctx.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      Utils.ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) q"_root_.rx.Ctx.Owner.Unsafe"
      else ctx

    enclosingCtx
  }

  def resetExpr[T](c: blackbox.Context)(t: c.Tree) = c.Expr[T](c.untypecheck(t))


  type Id[T] = T
}
