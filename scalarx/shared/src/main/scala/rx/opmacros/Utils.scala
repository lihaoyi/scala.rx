package rx.opmacros

import rx.Rx

import scala.language.experimental.macros
import scala.reflect.macros._
/**
  * Created by haoyi on 1/18/16.
  */
object Utils {
  /**
    * Walks a tree and injects in an implicit `Ctx.Owner` over any `Ctx.Owner` that
    * was previously inferred. This is done because by the time the macro runs,
    * implicits have already been resolved, so we cannot rely on implicit
    * resolution to do this for us
    */
  def injectRxCtx[T](c: Context)
                    (src: c.Tree,
                     newCtx: c.universe.TermName,
                     curCtxTree: c.Tree,
                     compileTime: c.Type,
                     unsafe: c.Type)
                    : c.Tree = {
    import c.universe._
    object transformer extends c.universe.Transformer {
      override def transform(tree: c.Tree): c.Tree = {
        if (curCtxTree.isEmpty) q"$newCtx"
        else if (tree.tpe == null) tree
        else if (tree.tpe =:= compileTime) q"$newCtx"
        else if (tree.equalsStructure(curCtxTree)) q"$newCtx"
        else if (tree.tpe =:= unsafe) q"$newCtx"
        else super.transform(tree)
      }
    }
    transformer.transform(src)
  }

  def ensureStaticEnclosingOwners(c: Context)(chk: c.Symbol, abortOnFail: Boolean): Boolean = {
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

  def buildSafeCtx[T: c.WeakTypeTag](c: Context)(): c.Expr[T] = {
    import c.universe._

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Owner], withMacrosDisabled = true)

    val isCompileTimeCtx = inferredCtx.isEmpty || inferredCtx.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(rx.Compat.enclosingName(c), abortOnFail = true)

    val safeCtx =
      if(isCompileTimeCtx) q"rx.Ctx.Owner.Unsafe"
      else if(rx.Compat.enclosingName(c).fullName == inferredCtx.symbol.fullName) q"rx.Ctx.Owner.Unsafe"
      else q"$inferredCtx"

    c.Expr[T](safeCtx)
  }

  def encCtx(c: Context)(ctx: c.Tree): c.Tree = {
    import c.universe._
    val isCompileTimeCtx = ctx.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(rx.Compat.enclosingName(c), abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) q"rx.Ctx.Owner.Unsafe"
      else ctx

    enclosingCtx
  }

  def buildMacro[T: c.WeakTypeTag]
                (c: Context)
                (func: c.Expr[T])
                (ownerCtx: c.Expr[rx.Ctx.Owner], dataCtx: c.Expr[rx.Ctx.Data])
                : c.Expr[Rx[T]] = {
    import c.universe._

    val newDataCtx =  c.fresh[TermName]("rxDataCtx")
    val newOwnerCtx =  c.fresh[TermName]("rxOwnerCtx")

    val isCompileTimeCtx = ownerCtx.tree.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(rx.Compat.enclosingName(c), abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) q"rx.Ctx.Owner.Unsafe"
      else ownerCtx

    val injected1 = injectRxCtx(c)(
      func.tree,
      newOwnerCtx,
      ownerCtx.tree,
      c.weakTypeOf[rx.Ctx.Owner.CompileTime.type],
      c.weakTypeOf[rx.Ctx.Owner.Unsafe.type]
    )

    val injected2 = injectRxCtx(c)(
      injected1,
      newDataCtx,
      dataCtx.tree,
      c.weakTypeOf[rx.Ctx.Data.CompileTime.type],
      c.weakTypeOf[rx.Ctx.Data.Unsafe.type]
    )

    val res = q"""rx.Rx.build{
      ($newOwnerCtx: rx.Ctx.Owner, $newDataCtx: rx.Ctx.Data) => $injected2
    }"""
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def buildUnsafe[T: c.WeakTypeTag](c: Context)(func: c.Expr[T]): c.Expr[Rx[T]] = {
    import c.universe._

    val inferredOwner = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Owner])
    val inferredData = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Data])

    require(!inferredOwner.isEmpty)

    val newDataCtx =  c.fresh[TermName]("rxDataCtx")
    val newOwnerCtx =  c.fresh[TermName]("rxOwnerCtx")

    val unsafeOwner =
      if(inferredOwner.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type])
        q"rx.Ctx.Owner.Unsafe"
      else if(inferredOwner.isEmpty)
        q"rx.Ctx.Owner.Unsafe"
      else
        inferredOwner

    val injected = injectRxCtx(c)(
      func.tree,
      newOwnerCtx,
      inferredOwner,
      c.weakTypeOf[rx.Ctx.Owner.CompileTime.type],
      c.weakTypeOf[rx.Ctx.Owner.Unsafe.type]
    )
    val injected2 = injectRxCtx(c)(
      injected,
      newDataCtx,
      inferredData,
      c.weakTypeOf[rx.Ctx.Data.CompileTime.type],
      c.weakTypeOf[rx.Ctx.Data.Unsafe.type]
    )
    val res = q"""rx.Rx.build{
      ($newOwnerCtx: rx.Ctx.Owner, $newDataCtx: rx.Ctx.Data) => $injected2
    }($unsafeOwner)"""
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def voodooRxCtx[T: c.WeakTypeTag](c: Context): c.Expr[T] = {
    import c.universe._
    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[T], withMacrosDisabled = true)
    val isCompileTime = inferredCtx.isEmpty
    val staticContext = ensureStaticEnclosingOwners(c)(rx.Compat.enclosingName(c), abortOnFail = false)
    val implicitCtx =
      if(isCompileTime && staticContext) q"${c.prefix}.Unsafe"
      else if(isCompileTime && !staticContext) q"${c.prefix}.CompileTime"
      else q"$inferredCtx"
    c.Expr[T](c.resetLocalAttrs(implicitCtx))
  }
}
