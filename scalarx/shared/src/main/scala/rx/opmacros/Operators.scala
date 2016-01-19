package rx.opmacros

import rx.opmacros.Utils._
import rx.{Node, Rx}

import scala.language.experimental.macros
import scala.reflect.macros._

object Operators {
  def initialize(c: Context)(f: c.Tree, owner: c.Tree) = {
    import c.universe._
    val newDataCtx =  c.fresh[TermName]("rxDataCtx")
    val newOwnerCtx =  c.fresh[TermName]("rxOwnerCtx")
    val newFunc = injectRxCtx(c)(
      f,
      newOwnerCtx,
      owner,
      c.weakTypeOf[rx.Ctx.Owner.CompileTime.type],
      c.weakTypeOf[rx.Ctx.Owner.Unsafe.type]
    )
    val enclosingCtx = encCtx(c)(owner)
    val newTree = q"($newOwnerCtx: rx.Ctx.Owner, $newDataCtx: rx.Ctx.Data) => $newFunc"
    (newTree, newOwnerCtx, enclosingCtx)
  }
  def filtered[In: c.WeakTypeTag, T: c.WeakTypeTag]
              (c: Context)
              (f: c.Expr[In => Boolean])
              (owner: c.Tree): c.Expr[Rx[T]] = {
    import c.universe._
    val (checkFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, owner)
    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val res = c.Expr[rx.Rx[T]](q"""
      ${c.prefix}.macroImpls.filterImpl($initValue, $checkFunc, $enclosingCtx)
    """)
    c.Expr[Rx[T]](c.resetLocalAttrs(res.tree))
  }

  type Id[T] = T

  def folded[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
            (c: Context)
            (start: c.Tree)
            (f: c.Tree)
            (owner: c.Tree)
            (implicit w: c.WeakTypeTag[Wrap[_]]): c.Tree = {

    import c.universe._
    val (foldFunc, newCtx, enclosingCtx) = initialize(c)(f, owner)
    val res = c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.foldImpl($start, $foldFunc, $enclosingCtx)
    """)
    res
  }

  def mapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
            (c: Context)
            (f: c.Tree)
            (owner: c.Tree)
            (implicit w: c.WeakTypeTag[Wrap[_]])
            : c.Tree = {

    import c.universe._
    val (call, newCtx, enclosingCtx) = initialize(c)(f, owner)

    val res = c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.mappedImpl($call, $enclosingCtx)
    """)
    res
  }

  def flatMapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
                (c: Context)
                (f: c.Tree)
                (owner: c.Tree)
                (implicit w: c.WeakTypeTag[Wrap[_]])
                : c.Tree = {

    import c.universe._
    val (call, newCtx, enclosingCtx) = initialize(c)(f, owner)

    val res = c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.flatMappedImpl($call, $enclosingCtx)
    """)
    res
  }


  def reduced[T: c.WeakTypeTag, Wrap[_]]
             (c: Context)
             (f: c.Tree)
             (owner: c.Tree)
             (implicit w: c.WeakTypeTag[Wrap[_]]): c.Tree = {
    import c.universe._
    val (reduceFunc, newCtx, enclosingCtx) = initialize(c)(f, owner)

    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val res = c.Expr[Rx[T]](q"""
      ${c.prefix}.macroImpls.reducedImpl($initValue, $reduceFunc, $enclosingCtx)
    """)

    c.resetLocalAttrs(res.tree)
  }


}
trait Operators[T, Wrap[_]]{
  def get[V](t: Node[V]): Wrap[V]
  def unwrap[V](t: Wrap[V]): V
  def prefix: Node[T]
  def flatMappedImpl[V](call: rx.Ctx.Owner => Wrap[T] => Wrap[Rx[V]],
                        enclosing: rx.Ctx.Owner): Rx[V] = {

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      this.unwrap(call(ownerCtx)(this.get(prefix))).apply()(dataCtx)
    }(enclosing)
  }
  def mappedImpl[V](call: rx.Ctx.Owner => Wrap[T] => Wrap[V],
                    enclosing: rx.Ctx.Owner): Rx[V] = {

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      this.unwrap(call(ownerCtx)(this.get(prefix)))
    }(enclosing)
  }

  def foldImpl[V](start: Wrap[V],
                  f: rx.Ctx.Owner => (Wrap[V], Wrap[T]) => Wrap[V],
                  enclosing: rx.Ctx.Owner): Rx[V] = {

    var prev: Wrap[V] = start
    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      prev = f(ownerCtx)(prev, this.get(prefix))
      this.unwrap(prev)
    }(enclosing)
  }

  /**
    * Split into two to make type-inference work
    */
  def reducedImpl(initValue: Wrap[T],
                  reduceFunc: rx.Ctx.Owner => (Wrap[T], Wrap[T]) => Wrap[T],
                  enclosing: rx.Ctx.Owner): Rx[T] = {
    var init = true
    def getPrev = this.get(prefix)

    var prev = getPrev

    def next: T = this.unwrap(prev)

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      if(init) {
        init = false
        prev = initValue
        next
      } else {
        prev = reduceFunc(ownerCtx)(prev, getPrev)
        next
      }
    }(enclosing)
  }

  def filterImpl(start: => Wrap[T],
                 f: rx.Ctx.Owner => Wrap[T] => Boolean,
                 enclosing: rx.Ctx.Owner) = {

    var init = true
    var prev = this.get(prefix)
    Rx.build {  (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      if(f(ownerCtx)(this.get(prefix)) || init) {
        init = false
        prev = start
      }

      this.unwrap(prev)
    }(enclosing)
  }

}
