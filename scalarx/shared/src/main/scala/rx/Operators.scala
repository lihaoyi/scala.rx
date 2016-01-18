package rx

import rx.Util._

import scala.language.experimental.macros
import scala.reflect.macros._
object Operators {


  def duplicate[T: c.WeakTypeTag](c: Context)(node: c.Expr[Var[T]])(ctx: c.Expr[RxCtx]): c.Expr[Var[T]] = {
    import c.universe._
    val inner = if(c.weakTypeOf[T] <:< c.weakTypeOf[Var[_]]) {
      val innerType = c.weakTypeTag[T].tpe.typeArgs.head
      q"Var.duplicate[$innerType]($node.now)($ctx)"
    } else {
      q"$node.now"
    }
    val res = q"Var($inner)"
    c.Expr[Var[T]](c.resetLocalAttrs(res))
  }



  def filtered[In: c.WeakTypeTag, T: c.WeakTypeTag](c: Context)(f: c.Expr[In => Boolean])(ctx: c.Expr[RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")

    
    val tTpe = c.weakTypeOf[T]
    def isHigher = c.weakTypeOf[In] <:< c.weakTypeOf[Var[_]]

    val initValue =
      if (isHigher) q"${c.prefix}.macroImpls.map(${c.prefix}.macroImpls.get(${c.prefix}.node))(Var.duplicate(_)($newCtx))"
      else q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val checkFunc = q"${transform(c)(f.tree,newCtx,ctx.tree)}"

    val res = c.Expr[rx.Rx[T]](q"""
      ${c.prefix}.macroImpls.filterImpl(
        ($newCtx: RxCtx) => $initValue,
        ${c.prefix}.node
      )(
        ($newCtx: RxCtx) => $checkFunc,
        rx.Node.getDownstream(${c.prefix}.node),
        ($newCtx: RxCtx) => ${encCtx(c)(ctx)},
        ${c.prefix}.macroImpls.get,
        ${c.prefix}.macroImpls.unwrap,
        $ctx
      )
    """)
    c.Expr[Rx[T]](c.resetLocalAttrs(res.tree))
  }

  type Id[T] = T

  def folded[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
            (c: Context)
            (start: c.Expr[V])
            (f: c.Expr[(V,T) => V])
            (ctx: c.Expr[RxCtx])
            (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx[T]] = {

    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val foldFunc = transform(c)(f.tree, newCtx, ctx.tree)

    val res = c.Expr[Rx[T]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.foldImpl(
        $start,
        ${c.prefix}.node
      )(
        ($newCtx: RxCtx) => $foldFunc,
        rx.Node.getDownstream(${c.prefix}.node),
        ${encCtx(c)(ctx)},
        ${c.prefix}.macroImpls.get,
        ${c.prefix}.macroImpls.unwrap,
        ${c.prefix}.macroImpls.unwrap
      )
    """))
    res
  }

  def mapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
            (c: Context)
            (f: c.Expr[T => V])
            (ctx: c.Expr[RxCtx])
            (implicit w: c.WeakTypeTag[Wrap[_]])
            : c.Expr[Rx[V]] = {

    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tryTpe = c.weakTypeOf[scala.util.Try[_]]
    val call =  transform(c)(f.tree,newCtx,ctx.tree)

    val res = c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.mappedImpl(
        ${c.prefix}.node,
        ($newCtx: RxCtx) => $call
      )(
        rx.Node.getDownstream(${c.prefix}.node),
        ${encCtx(c)(ctx)},
        ${c.prefix}.macroImpls.get,
        ${c.prefix}.macroImpls.unwrap
      )
    """
    ))
    res
  }

  def flatMapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
                (c: Context)
                (f: c.Expr[Wrap[T] => Wrap[Rx[V]]])
                (ctx: c.Expr[RxCtx])
                (implicit w: c.WeakTypeTag[Wrap[_]])
                : c.Expr[Rx[V]] = {

    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val tryTpe = c.weakTypeOf[scala.util.Try[_]]
    val call =  transform(c)(f.tree,newCtx,ctx.tree)

    val res = c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.flatMappedImpl(
        ${c.prefix}.node,
        ($newCtx: RxCtx) => $call
      )(
        rx.Node.getDownstream(${c.prefix}.node),
        ${encCtx(c)(ctx)},
        ${c.prefix}.macroImpls.get,
        ${c.prefix}.macroImpls.unwrap
      )
    """
    ))
    res
  }

  class Operator[Wrap[_]]{
    def flatMappedImpl[T, V](prefix: Node[T],
                         call: RxCtx => Wrap[T] => Wrap[Rx[V]])
                        (downStream: Seq[Node[_]],
                         enclosing: RxCtx,
                         toT: Node[T] => Wrap[T],
                         toOut: Wrap[Rx[V]] => Rx[V]): Rx[V] = {

      Rx.build { implicit newCtx: RxCtx =>
        downStream.foreach(_.Internal.addDownstream(newCtx))
        toOut(call(newCtx)(toT(prefix))).apply()
      }(enclosing)
    }
    def mappedImpl[T, V](prefix: Node[T],
                         call: RxCtx => Wrap[T] => Wrap[V])
                        (downStream: Seq[Node[_]],
                         enclosing: RxCtx,
                         toT: Node[T] => Wrap[T],
                         toOut: Wrap[V] => V): Rx[V] = {

      Rx.build { implicit newCtx: RxCtx =>
        downStream.foreach(_.Internal.addDownstream(newCtx))
        toOut(call(newCtx)(toT(prefix)))
      }(enclosing)
    }

    def foldImpl[T, V](start: Wrap[V],
                       prefix: Node[T])
                      (f: RxCtx => (Wrap[V], Wrap[T]) => Wrap[V],
                       downStream: Seq[Node[_]],
                       enclosing: RxCtx,
                       toT: Node[T] => Wrap[T],
                       toOut: Wrap[V] => V,
                       toOut2: Wrap[T] => T): Rx[V] = {

      var prev: Wrap[V] = start
      Rx.build { newCtx: RxCtx =>
        downStream.foreach(_.Internal.addDownstream(newCtx))
        prev = f(newCtx)(prev, toT(prefix))
        toOut(prev)
      }(enclosing)
    }

    /**
      * Split into two to make type-inference work
      */
    def reducedImpl[T](initValue: Wrap[T],
                        prefix: Node[T])
                       (reduceFunc: (Wrap[T], Wrap[T]) => Wrap[T],
                        toT: Node[T] => Wrap[T],
                        toOut: Wrap[T] => T,
                        enclosing: RxCtx,
                        downStream: Seq[Node[_]]): Rx[T] = {
      var init = true
      def getPrev = toT(prefix)

      var prev = getPrev

      def next: T = toOut(prev)

      Rx.build { newCtx: RxCtx =>
        downStream.foreach(_.Internal.addDownstream(newCtx))
        if(init) {
          init = false
          prev = initValue
          next
        } else {
          prev = reduceFunc(prev, getPrev)
          next
        }
      }(enclosing)
    }

    def filterImpl[T, Out](start: RxCtx => T,
                           prefix: Node[Out])
                          (f: RxCtx => T => Boolean,
                           downStream: Seq[Node[_]],
                           enclosing: RxCtx => RxCtx,
                           toT: Node[Out] => T,
                           toOut: T => Out,
                           ctx: RxCtx) = {

      var init = true
      var prev = toT(prefix)
      Rx.build { newCtx: RxCtx =>
        downStream.foreach(_.Internal.addDownstream(newCtx))
        if(f(newCtx)(toT(prefix)) || init) {
          init = false
          prev = start(newCtx)
        }
        toOut(prev)
      }(enclosing(ctx))
    }

  }

  def reduced[T: c.WeakTypeTag, Wrap[_]]
             (c: Context)
             (f: c.Expr[(Wrap[T], Wrap[T]) => Wrap[T]])
             (ctx: c.Expr[RxCtx])
             (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx[T]] = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val isHigher = c.weakTypeOf[T] <:< c.weakTypeOf[Var[_]]

    val reduceFunc = transform(c)(f.tree,newCtx,ctx.tree)

    val initValue =
      if (isHigher) q"${c.prefix}.macroImpls.map(${c.prefix}.macroImpls.get(${c.prefix}.node))(Var.duplicate(_)(implicitly))"
      else q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val res = c.Expr[Rx[T]](q"""
      ${c.prefix}.macroImpls.reducedImpl[${weakTypeOf[T]}](
        $initValue,
        ${c.prefix}.node
      )(
        $reduceFunc,
        ${c.prefix}.macroImpls.get,
        ${c.prefix}.macroImpls.unwrap,
        ${encCtx(c)(ctx)},
        rx.Node.getDownstream(${c.prefix}.node)
      )
    """)

    c.Expr[Rx[T]](c.resetLocalAttrs(res.tree))
  }
}
