package rx

import scala.language.experimental.macros
import scala.reflect.macros._

object Macros {

  def transform[T](c: Context)(src: c.Tree, newCtx: c.universe.TermName, curCtxTree: c.Tree): c.Tree = {
    import c.universe._
    object transformer extends c.universe.Transformer {
      override def transform(tree: c.Tree): c.Tree = {
        if (curCtxTree.isEmpty) q"$newCtx"
        else if (tree.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]) q"$newCtx"
        else if (tree.equalsStructure(curCtxTree)) q"$newCtx"
        else if (tree.tpe =:= c.weakTypeOf[rx.RxCtx.Unsafe.type]) q"$newCtx"
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

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[rx.RxCtx], withMacrosDisabled = true)

    val isCompileTimeCtx = inferredCtx.isEmpty || inferredCtx.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]

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
    val isCompileTimeCtx = ctx.tree.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else ctx

    enclosingCtx
  }

  def addDownstreamOfAll[T: c.WeakTypeTag](c: Context)(node: c.Expr[rx.Node[T]])(ctx: c.Expr[RxCtx]): c.Expr[Unit] = {
    import c.universe._
    val next = if(c.weakTypeOf[T] <:< c.weakTypeOf[rx.Node[_]]) {
      val innerType = c.weakTypeTag[T].tpe.typeArgs.head
      q"rx.Node.addDownstreamOfAll[$innerType]($node.now)($ctx)"
    } else {
      q"()"
    }
    val res = q"{$node.Internal.addDownstream($ctx); $next }"
    c.Expr[Unit](c.resetLocalAttrs(res))
  }

  def duplicate[T: c.WeakTypeTag](c: Context)(node: c.Expr[rx.Var[T]])(ctx: c.Expr[RxCtx]): c.Expr[rx.Var[T]] = {
    import c.universe._
    val inner = if(c.weakTypeOf[T] <:< c.weakTypeOf[rx.Var[_]]) {
      val innerType = c.weakTypeTag[T].tpe.typeArgs.head
      q"rx.Var.duplicate[$innerType]($node.now)($ctx)"
    } else {
      q"$node.now"
    }
    val res = q"rx.Var($inner)"
    c.Expr[rx.Var[T]](c.resetLocalAttrs(res))
  }

  def mapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Out: c.WeakTypeTag](c: Context)(f: c.Expr[T => V])(ctx: c.Expr[rx.RxCtx]): c.Expr[Rx[Out]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tryTpe = c.weakTypeOf[scala.util.Try[_]]
    val tPrefix = transform(c)(c.prefix.tree,newCtx,ctx.tree)
    val call = if(c.weakTypeOf[T] <:< tryTpe && c.weakTypeOf[V] <:< tryTpe) {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}($tPrefix.node.toTry).get"
    } else {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}($tPrefix.node.now)"
    }
    val res = c.Expr[Rx[Out]](
      q"""rx.Rx.build { implicit $newCtx: rx.RxCtx =>
          rx.Node.addDownstreamOfAll($tPrefix.node)($newCtx)
          $call
        }(${encCtx(c)(ctx)})
      """)
    c.Expr[rx.Rx[Out]](c.resetLocalAttrs(res.tree))
  }

  def flatMapped[T: c.WeakTypeTag, V: c.WeakTypeTag](c: Context)(f: c.Expr[T => Rx[V]])(ctx: c.Expr[rx.RxCtx]): c.Expr[Rx[V]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tryTpe = c.weakTypeOf[scala.util.Try[_]]
    val tPrefix = transform(c)(c.prefix.tree,newCtx,ctx.tree)
    val call = if(c.weakTypeOf[T] <:< tryTpe) {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}($tPrefix.node.toTry)"
    } else {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}($tPrefix.node.now)"
    }
    val res = c.Expr[Rx[V]](
      q"""rx.Rx.build { $newCtx: rx.RxCtx =>
          rx.Node.addDownstreamOfAll($tPrefix.node)($newCtx)
          $call()($newCtx)
        }(${encCtx(c)(ctx)})
      """)
    c.Expr[rx.Rx[V]](c.resetLocalAttrs(res.tree))
  }

  def filtered[In: c.WeakTypeTag, T: c.WeakTypeTag, OpsCtx <: rx.OpsContext : c.WeakTypeTag](c: Context)(f: c.Expr[In => Boolean])(ctx: c.Expr[rx.RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tPrefix = transform(c)(c.prefix.tree,newCtx,ctx.tree)
    val tTpe = c.weakTypeOf[T]
    val isSafe = c.weakTypeOf[OpsCtx] <:< c.weakTypeOf[rx.SafeContext.type]
    def isHigher = c.weakTypeOf[In] <:< c.weakTypeOf[rx.Var[_]]

    val init =
      if(isHigher && !isSafe) q"rx.Var.duplicate($tPrefix.node.now)($newCtx)"
      else if (isHigher && isSafe) q"$tPrefix.node.toTry.map(in => rx.Var.duplicate(in)($newCtx))"
      else if (!isHigher && !isSafe) q"$tPrefix.node.now"
      else q"$tPrefix.node.toTry"

    val checkFunc = if(isSafe) {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}($tPrefix.node.toTry)"
    } else {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}($tPrefix.node.now)"
    }

    val res = c.Expr[Rx[T]](
      q"""{
        var init = true
        var prev = ${if(isSafe) q"${c.prefix}.node.toTry" else q"${c.prefix}.node.now" }
        rx.Rx.build { $newCtx: rx.RxCtx =>
          rx.Node.addDownstreamOfAll($tPrefix.node)($newCtx)
          if($checkFunc || init) {
            init = false
            prev = $init
            ${if(isSafe) q"prev.get" else q"prev"}
          }
          else ${if(isSafe) q"prev.get" else q"prev"}
        }(${encCtx(c)(ctx)})
      }""")
    c.Expr[rx.Rx[T]](c.resetLocalAttrs(res.tree))
  }

  def folded[T: c.WeakTypeTag, V: c.WeakTypeTag, Out: c.WeakTypeTag, OpsCtx <: rx.OpsContext : c.WeakTypeTag](c: Context)(start: c.Expr[V])(f: c.Expr[(V,T) => V])(ctx: c.Expr[rx.RxCtx]): c.Expr[Rx[Out]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tPrefix = transform(c)(c.prefix.tree,newCtx,ctx.tree)
    val isSafe = c.weakTypeOf[OpsCtx] <:< c.weakTypeOf[rx.SafeContext.type]
    val appliedFoldFunc = if(isSafe) {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}(prev,$tPrefix.node.toTry)"
    } else {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}(prev,$tPrefix.node.now)"
    }

    val res = c.Expr[Rx[Out]](
      q"""{
            var prev = $start
            rx.Rx.build { $newCtx: rx.RxCtx =>
              rx.Node.addDownstreamOfAll($tPrefix.node)($newCtx)
              prev = $appliedFoldFunc
              ${if(isSafe) q"prev.get" else q"prev"}
            }(${encCtx(c)(ctx)})
          }
      """)
    c.Expr[rx.Rx[Out]](c.resetLocalAttrs(res.tree))
  }

  def reduced[T: c.WeakTypeTag, Out: c.WeakTypeTag, OpsCtx <: rx.OpsContext : c.WeakTypeTag](c: Context)(f: c.Expr[(T,T) => T])(ctx: c.Expr[rx.RxCtx]): c.Expr[Rx[Out]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tPrefix = transform(c)(c.prefix.tree,newCtx,ctx.tree)
    val isSafe = c.weakTypeOf[OpsCtx] <:< c.weakTypeOf[rx.SafeContext.type]
    val isHigher = c.weakTypeOf[Out] <:< c.weakTypeOf[rx.Var[_]]

    val appliedReduceFunc = if(isSafe) {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}(prev,$tPrefix.node.toTry)"
    } else {
      q"${transform(c)(f.tree,newCtx,ctx.tree)}(prev,$tPrefix.node.now)"
    }

    val init =
      if(isHigher && !isSafe) q"rx.Var.duplicate($tPrefix.node.now)($newCtx)"
      else if (isHigher && isSafe) q"$tPrefix.node.toTry.map(in => rx.Var.duplicate(in)($newCtx))"
      else if (!isHigher && !isSafe) q"$tPrefix.node.now"
      else q"$tPrefix.node.toTry"

    val res = c.Expr[Rx[Out]](
      q"""{
        var init = true
        var prev = ${if(isSafe) q"${c.prefix}.node.toTry" else q"${c.prefix}.node.now" }
        rx.Rx.build { $newCtx: rx.RxCtx =>
          rx.Node.addDownstreamOfAll($tPrefix.node)($newCtx)
          if(init) {
            init = false
            prev = $init
            ${if(isSafe) q"prev.get" else q"prev"}
          } else {
            prev = $appliedReduceFunc
            ${if(isSafe) q"prev.get" else q"prev"}
          }
        }(${encCtx(c)(ctx)})
      }""")
    c.Expr[rx.Rx[Out]](c.resetLocalAttrs(res.tree))
  }

  def buildMacro[T: c.WeakTypeTag](c: Context)(func: c.Expr[T])(curCtx: c.Expr[rx.RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._

    val newCtx =  c.fresh[TermName]("rxctx")

    val isCompileTimeCtx = curCtx.tree.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]

    if(isCompileTimeCtx)
      ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val enclosingCtx =
      if(isCompileTimeCtx) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else curCtx

    val res = q"rx.Rx.build{$newCtx: rx.RxCtx => ${transform(c)(func.tree, newCtx, curCtx.tree)}}($enclosingCtx)"
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def buildUnsafe[T: c.WeakTypeTag](c: Context)(func: c.Expr[T]): c.Expr[Rx[T]] = {
    import c.universe._

    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[rx.RxCtx])

    require(!inferredCtx.isEmpty)

    val newCtx = c.fresh[TermName]("rxctx")

    val enclosingCtx =
      if(inferredCtx.tpe =:= c.weakTypeOf[rx.RxCtx.CompileTime.type]) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else if(inferredCtx.isEmpty) c.Expr[RxCtx](q"rx.RxCtx.Unsafe")
      else c.Expr[RxCtx](q"$inferredCtx")

    val res = q"rx.Rx.build{$newCtx: rx.RxCtx => ${transform(c)(func.tree, newCtx, inferredCtx)}}($enclosingCtx)"
    c.Expr[Rx[T]](c.resetLocalAttrs(res))
  }

  def buildImplicitRxCtx(c: Context): c.Expr[RxCtx] = {
    import c.universe._
    val inferredCtx = c.inferImplicitValue(c.weakTypeOf[rx.RxCtx], withMacrosDisabled = true)
    val isCompileTime = inferredCtx.isEmpty
    val staticContext = ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = false)
    val implicitCtx =
      if(isCompileTime && staticContext) q"rx.RxCtx.Unsafe"
      else if(isCompileTime && !staticContext) q"rx.RxCtx.CompileTime"
      else q"$inferredCtx"
    c.Expr[RxCtx](c.resetLocalAttrs(implicitCtx))
  }
}
