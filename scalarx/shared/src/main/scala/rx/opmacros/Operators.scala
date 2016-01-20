package rx.opmacros

import rx.opmacros.Utils._
import rx.{Node, Rx}

import scala.language.experimental.macros
import scala.reflect.macros._

object Operators {
  def initialize(c: Context)(f: c.Tree, owner: c.Tree, data: c.Tree) = {
    import c.universe._
    val newDataCtx =  c.fresh[TermName]("rxDataCtx")
    val newOwnerCtx =  c.fresh[TermName]("rxOwnerCtx")
    val newFunc2 = doubleInject(c)(f, newOwnerCtx, owner, newDataCtx, data)
    val enclosingCtx = encCtx(c)(owner)
    val newTree = q"($newOwnerCtx: rx.Ctx.Owner, $newDataCtx: rx.Ctx.Data) => $newFunc2"
    (newTree, newOwnerCtx, enclosingCtx)
  }
  def filtered[In: c.WeakTypeTag, T: c.WeakTypeTag]
              (c: Context)
              (f: c.Expr[In => Boolean])
              (ownerCtx: c.Expr[rx.Ctx.Owner], dataCtx: c.Expr[rx.Ctx.Data]): c.Expr[Rx[T]] = {
    import c.universe._
    val (checkFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree, dataCtx.tree)
    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    val res = c.Expr[rx.Rx[T]](q"""
      ${c.prefix}.macroImpls.filterImpl($initValue, $checkFunc, $enclosingCtx)
    """)
    c.Expr[Rx[T]](c.resetLocalAttrs(res.tree))
  }

  type Id[T] = T

  def folded[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
            (c: Context)
            (start: c.Expr[Wrap[V]])
            (f: c.Expr[(Wrap[V], Wrap[T]) => Wrap[V]])
            (ownerCtx: c.Expr[rx.Ctx.Owner], dataCtx: c.Expr[rx.Ctx.Data])
            (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx[V]] = {

    import c.universe._
    val (foldFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree, dataCtx.tree)
    c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.foldImpl($start, $foldFunc, $enclosingCtx)
    """))
  }

  def mapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
            (c: Context)
            (f: c.Expr[Wrap[T] => Wrap[V]])
            (ownerCtx: c.Expr[rx.Ctx.Owner], dataCtx: c.Expr[rx.Ctx.Data])
            (implicit w: c.WeakTypeTag[Wrap[_]])
            : c.Expr[Rx[V]] = {

    import c.universe._
    val (call, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree, dataCtx.tree)

    c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.mappedImpl($call, $enclosingCtx)
    """))
  }

  def flatMapped[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
                (c: Context)
                (f: c.Expr[Wrap[T] => Wrap[Rx[V]]])
                (ownerCtx: c.Expr[rx.Ctx.Owner], dataCtx: c.Expr[rx.Ctx.Data])
                (implicit w: c.WeakTypeTag[Wrap[_]])
                : c.Expr[Rx[V]] = {

    import c.universe._
    val (call, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree, dataCtx.tree)

    c.Expr[Rx[V]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.flatMappedImpl($call, $enclosingCtx)
    """))
  }


  def reduced[T: c.WeakTypeTag, Wrap[_]]
             (c: Context)
             (f: c.Expr[(Wrap[T], Wrap[T]) => Wrap[T]])
             (ownerCtx: c.Expr[rx.Ctx.Owner], dataCtx: c.Expr[rx.Ctx.Data])
             (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx[T]] = {
    import c.universe._
    val (reduceFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree, dataCtx.tree)

    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    c.Expr[Rx[T]](c.resetLocalAttrs(q"""
      ${c.prefix}.macroImpls.reducedImpl($initValue, $reduceFunc, $enclosingCtx)
    """))

  }


}
trait Operators[T, Wrap[_]]{
  def get[V](t: Node[V]): Wrap[V]
  def unwrap[V](t: Wrap[V]): V
  def prefix: Node[T]
  def flatMappedImpl[V](call: (rx.Ctx.Owner, rx.Ctx.Data) => Wrap[T] => Wrap[Rx[V]],
                        enclosing: rx.Ctx.Owner): Rx[V] = {

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      this.unwrap(call(ownerCtx, dataCtx)(this.get(prefix))).apply()(dataCtx)
    }(enclosing)
  }
  def mappedImpl[V](call: (rx.Ctx.Owner, rx.Ctx.Data) => Wrap[T] => Wrap[V],
                    enclosing: rx.Ctx.Owner): Rx[V] = {

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      this.unwrap(call(ownerCtx, dataCtx)(this.get(prefix)))
    }(enclosing)
  }

  def foldImpl[V](start: Wrap[V],
                  f: (rx.Ctx.Owner, rx.Ctx.Data) => (Wrap[V], Wrap[T]) => Wrap[V],
                  enclosing: rx.Ctx.Owner): Rx[V] = {

    var prev: Wrap[V] = start
    Rx.build { (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      prev = f(ownerCtx, dataCtx)(prev, this.get(prefix))
      this.unwrap(prev)
    }(enclosing)
  }

  /**
    * Split into two to make type-inference work
    */
  def reducedImpl(initValue: Wrap[T],
                  reduceFunc: (rx.Ctx.Owner, rx.Ctx.Data) => (Wrap[T], Wrap[T]) => Wrap[T],
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
        prev = reduceFunc(ownerCtx, dataCtx)(prev, getPrev)
        next
      }
    }(enclosing)
  }

  def filterImpl(start: => Wrap[T],
                 f: (rx.Ctx.Owner, rx.Ctx.Data) => Wrap[T] => Boolean,
                 enclosing: rx.Ctx.Owner) = {

    var init = true
    var prev = this.get(prefix)
    Rx.build {  (ownerCtx, dataCtx) =>
      prefix.Internal.addDownstream(dataCtx)
      if(f(ownerCtx, dataCtx)(this.get(prefix)) || init) {
        init = false
        prev = start
      }

      this.unwrap(prev)
    }(enclosing)
  }

}
