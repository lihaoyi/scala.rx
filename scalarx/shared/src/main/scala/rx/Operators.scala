package rx

import rx.Util._

import scala.language.experimental.macros
import scala.reflect.macros._

object Operators {
  def initialize(c: Context)(f: c.Tree, ctx: c.Expr[RxCtx]) = {
    import c.universe._
    val newCtx =  c.fresh[TermName]("rxctx")
    val newFunc = transform(c)(f, newCtx, ctx.tree)
    (q"($newCtx: RxCtx) => $newFunc", newCtx)
  }
  def filtered[In: c.WeakTypeTag, T: c.WeakTypeTag](c: Context)(f: c.Expr[In => Boolean])(ctx: c.Expr[RxCtx]): c.Expr[Rx[T]] = {
    import c.universe._
    val (checkFunc, newCtx) = initialize(c)(f.tree, ctx)
    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val res = c.Expr[rx.Rx[T]](q"""
      ${c.prefix}.macroImpls.filterImpl(
        ($newCtx: RxCtx) => $initValue,
        ${c.prefix}.node,
         $checkFunc,
        ($newCtx: RxCtx) => ${encCtx(c)(ctx)},
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
    val (foldFunc, newCtx) = initialize(c)(f.tree, ctx)
    val res = c.Expr[Rx[T]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.foldImpl(
        $start,
        ${c.prefix}.node,
        $foldFunc,
        ${encCtx(c)(ctx)}
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
    val (call, newCtx) = initialize(c)(f.tree, ctx)

    val res = c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.mappedImpl(
        ${c.prefix}.node,
        $call,
        ${encCtx(c)(ctx)}
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
    val (call, newCtx) = initialize(c)(f.tree, ctx)

    val res = c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.flatMappedImpl(
        ${c.prefix}.node,
        $call,
        ${encCtx(c)(ctx)}
      )
    """
    ))
    res
  }


  def reduced[T: c.WeakTypeTag, Wrap[_]]
             (c: Context)
             (f: c.Expr[(Wrap[T], Wrap[T]) => Wrap[T]])
             (ctx: c.Expr[RxCtx])
             (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx[T]] = {
    import c.universe._
    val (reduceFunc, newCtx) = initialize(c)(f.tree, ctx)

    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val res = c.Expr[Rx[T]](q"""
      ${c.prefix}.macroImpls.reducedImpl(
        $initValue,
        ${c.prefix}.node,
        $reduceFunc,
        ${encCtx(c)(ctx)}
      )
    """)

    c.Expr[Rx[T]](c.resetLocalAttrs(res.tree))
  }


}
trait Operators[T, Wrap[_]]{
  def get[V](t: Node[V]): Wrap[V]
  def unwrap[V](t: Wrap[V]): V
  def flatMappedImpl[V](prefix: Node[T],
                           call: RxCtx => Wrap[T] => Wrap[Rx[V]],
                           enclosing: RxCtx): Rx[V] = {

    Rx.build { implicit newCtx: RxCtx =>
      prefix.Internal.addDownstream(newCtx)
      this.unwrap(call(newCtx)(this.get(prefix))).apply()
    }(enclosing)
  }
  def mappedImpl[V](prefix: Node[T],
                       call: RxCtx => Wrap[T] => Wrap[V],
                       enclosing: RxCtx): Rx[V] = {

    Rx.build { implicit newCtx: RxCtx =>
      prefix.Internal.addDownstream(newCtx)
      this.unwrap(call(newCtx)(this.get(prefix)))
    }(enclosing)
  }

  def foldImpl[V](start: Wrap[V],
                  prefix: Node[T],
                  f: RxCtx => (Wrap[V], Wrap[T]) => Wrap[V],
                  enclosing: RxCtx): Rx[V] = {

    var prev: Wrap[V] = start
    Rx.build { newCtx: RxCtx =>
      prefix.Internal.addDownstream(newCtx)
      prev = f(newCtx)(prev, this.get(prefix))
      this.unwrap(prev)
    }(enclosing)
  }

  /**
    * Split into two to make type-inference work
    */
  def reducedImpl(initValue: Wrap[T],
                  prefix: Node[T],
                  reduceFunc: RxCtx => (Wrap[T], Wrap[T]) => Wrap[T],
                  enclosing: RxCtx): Rx[T] = {
    var init = true
    def getPrev = this.get(prefix)

    var prev = getPrev

    def next: T = this.unwrap(prev)

    Rx.build { newCtx: RxCtx =>
      prefix.Internal.addDownstream(newCtx)
      if(init) {
        init = false
        prev = initValue
        next
      } else {
        prev = reduceFunc(newCtx)(prev, getPrev)
        next
      }
    }(enclosing)
  }

  def filterImpl(start: RxCtx => Wrap[T],
                 prefix: Node[T],
                 f: RxCtx => Wrap[T] => Boolean,
                 enclosing: RxCtx => RxCtx,
                 ctx: RxCtx) = {

    var init = true
    var prev = this.get(prefix)
    Rx.build { newCtx: RxCtx =>
      prefix.Internal.addDownstream(newCtx)
      if(f(newCtx)(this.get(prefix)) || init) {
        init = false
        prev = start(newCtx)
      }
      this.unwrap(prev)
    }(enclosing(ctx))
  }

}
