package rx.opmacros

import rx.opmacros.Utils._
import rx.{Rx,Var}

import scala.reflect.macros._

/**
  * Implementations for the various macros that Scala.rx defines for its operators.
  */
object Operators {
  def initialize(c: blackbox.Context)(f: c.Tree, owner: c.Tree) = {
    import c.universe._
    val data = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Data])
    val newDataCtx =  c.freshName(TermName("rxDataCtx"))
    val newOwnerCtx =  c.freshName(TermName("rxOwnerCtx"))
    val newFunc2 = doubleInject(c)(f, newOwnerCtx, owner, newDataCtx, data)
    val enclosingCtx = Utils.enclosingCtx(c)(owner)
    val newTree = q"($newOwnerCtx: _root_.rx.Ctx.Owner, $newDataCtx: _root_.rx.Ctx.Data) => $newFunc2"
    (newTree, newOwnerCtx, enclosingCtx)
  }

  def filter[In: c.WeakTypeTag, T: c.WeakTypeTag]
              (c: blackbox.Context)
              (f: c.Expr[In => Boolean])
              (ownerCtx: c.Expr[rx.Ctx.Owner]): c.Expr[Rx.Dynamic[T]] = {
    import c.universe._
    val (checkFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree)
    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"
    resetExpr[Rx.Dynamic[T]](c)(q"""
      ${c.prefix}.macroImpls.filterImpl($initValue, $checkFunc, $enclosingCtx)
    """)
  }

  def fold[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
          (c: blackbox.Context)
          (start: c.Expr[Wrap[V]])
          (f: c.Expr[(Wrap[V], Wrap[T]) => Wrap[V]])
          (ownerCtx: c.Expr[rx.Ctx.Owner])
          (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx.Dynamic[V]] = {

    import c.universe._
    val (foldFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree)
    resetExpr[Rx.Dynamic[V]](c)(q"""
      ${c.prefix}.macroImpls.foldImpl($start, $foldFunc, $enclosingCtx)
    """)
  }

  def map[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
         (c: blackbox.Context)
         (f: c.Expr[Wrap[T] => Wrap[V]])
         (ownerCtx: c.Expr[rx.Ctx.Owner])
         (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx.Dynamic[V]] = {

    import c.universe._
    val (call, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree)

    Utils.resetExpr[Rx.Dynamic[V]](c)(q"""
      ${c.prefix}.macroImpls.mappedImpl($call, $enclosingCtx)
    """)
  }

  def flatMap[T: c.WeakTypeTag, V: c.WeakTypeTag, Wrap[_]]
             (c: blackbox.Context)
             (f: c.Expr[Wrap[T] => Wrap[Rx[V]]])
             (ownerCtx: c.Expr[rx.Ctx.Owner])
             (implicit w: c.WeakTypeTag[Wrap[_]])
             : c.Expr[Rx.Dynamic[V]] = {

    import c.universe._
    val (call, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree)

    resetExpr[Rx.Dynamic[V]](c)(q"""
      ${c.prefix}.macroImpls.flatMappedImpl($call, $enclosingCtx)
    """)
  }


  def reduce[T: c.WeakTypeTag, Wrap[_]]
            (c: blackbox.Context)
            (f: c.Expr[(Wrap[T], Wrap[T]) => Wrap[T]])
            (ownerCtx: c.Expr[rx.Ctx.Owner])
            (implicit w: c.WeakTypeTag[Wrap[_]]): c.Expr[Rx.Dynamic[T]] = {
    import c.universe._
    val (reduceFunc, newCtx, enclosingCtx) = initialize(c)(f.tree, ownerCtx.tree)

    val initValue = q"${c.prefix}.macroImpls.get(${c.prefix}.node)"

    resetExpr[Rx.Dynamic[T]](c)(q"""
      ${c.prefix}.macroImpls.reducedImpl($initValue, $reduceFunc, $enclosingCtx)
    """)
  }
}

/**
  * Non-macro runtime implementations for the functions that Scala.rx's macros
  * forward to. This is parametrized on a [[Wrap]]per type, to be re-usable for
  * both operators dealing with `T` and `Try[T]`.
  *
  * Provides a small number of helpers to deal with generically dealing with
  * `Wrap[T]`s
  */
trait Operators[T, Wrap[_]] {

  def get[V](t: Rx[V]): Wrap[V]

  def unwrap[V](t: Wrap[V]): V

  def prefix: Rx[T]

  def flatMappedImpl[V](call: (rx.Ctx.Owner, rx.Ctx.Data) => Wrap[T] => Wrap[Rx[V]],
                        enclosing: rx.Ctx.Owner)(implicit name: sourcecode.Name): Rx.Dynamic[V] = {

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.addDownstream(dataCtx)
      val inner = this.unwrap(call(ownerCtx, dataCtx)(this.get(prefix)))
      inner.downStream.add(dataCtx.contextualRx)
      inner.now
    }(enclosing, name)
  }

  def mappedImpl[V](call: (rx.Ctx.Owner, rx.Ctx.Data) => Wrap[T] => Wrap[V],
                    enclosing: rx.Ctx.Owner)(implicit name: sourcecode.Name): Rx.Dynamic[V] = {

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.addDownstream(dataCtx)
      this.unwrap(call(ownerCtx, dataCtx)(this.get(prefix)))
    }(enclosing,name)
  }

  def foldImpl[V](start: Wrap[V],
                  f: (rx.Ctx.Owner, rx.Ctx.Data) => (Wrap[V], Wrap[T]) => Wrap[V],
                  enclosing: rx.Ctx.Owner)(implicit name: sourcecode.Name): Rx.Dynamic[V] = {

    var prev: Wrap[V] = start
    Rx.build { (ownerCtx, dataCtx) =>
      prefix.addDownstream(dataCtx)
      prev = f(ownerCtx, dataCtx)(prev, this.get(prefix))
      this.unwrap(prev)
    }(enclosing,name)
  }

  /**
    * Split into two to make type-inference work
    */
  def reducedImpl(initValue: Wrap[T],
                  reduceFunc: (rx.Ctx.Owner, rx.Ctx.Data) => (Wrap[T], Wrap[T]) => Wrap[T],
                  enclosing: rx.Ctx.Owner)(implicit name: sourcecode.Name): Rx.Dynamic[T] = {
    var init = true

    def getPrev = this.get(prefix)

    var prev = getPrev

    def next: T = this.unwrap(prev)

    Rx.build { (ownerCtx, dataCtx) =>
      prefix.addDownstream(dataCtx)
      if (init) {
        init = false
        prev = initValue
        next
      } else {
        prev = reduceFunc(ownerCtx, dataCtx)(prev, getPrev)
        next
      }
    }(enclosing,name)
  }


  def filterImpl(start: => Wrap[T],
                 f: (rx.Ctx.Owner, rx.Ctx.Data) => Wrap[T] => Boolean,
                 enclosing: rx.Ctx.Owner)(implicit name: sourcecode.Name): Rx.Dynamic[T] = {

    var init = true
    var prev = this.get(prefix)
    Rx.build { (ownerCtx, dataCtx) =>
      prefix.addDownstream(dataCtx)
      if (f(ownerCtx, dataCtx)(this.get(prefix)) || init) {
        init = false
        prev = start
      }

      this.unwrap(prev)
    }(enclosing,name)
  }
}

object MapReadMacro {
  def impl[T : c.WeakTypeTag](c: blackbox.Context)(read: c.Expr[Var[T] => T])(ownerCtx: c.Expr[rx.Ctx.Owner]): c.Expr[Var[T]] = {
    import c.universe._

    val dataCtx = c.inferImplicitValue(c.weakTypeOf[rx.Ctx.Data])
    val newDataCtx =  c.freshName(TermName("rxDataCtx"))
    val newOwnerCtx =  c.freshName(TermName("rxOwnerCtx"))
    val baseValue = c.freshName(TermName("base"))
    val functionValue = c.freshName(TermName("function"))
    val rxPkg = q"_root_.rx"

    val isCompileTimeCtx = ownerCtx.tree.tpe =:= c.weakTypeOf[rx.Ctx.Owner.CompileTime.type]

    if(isCompileTimeCtx)
      Utils.ensureStaticEnclosingOwners(c)(c.internal.enclosingOwner, abortOnFail = true)

    val q"($param) => $body" = read.tree
    val injected2 = Utils.doubleInject(c)(body, newOwnerCtx, ownerCtx.tree, newDataCtx, dataCtx)

    val tType = weakTypeOf[T]
    val tree = q"""
      val $baseValue = ${c.prefix}.base
      val $functionValue = ($param, $newOwnerCtx: $rxPkg.Ctx.Owner, $newDataCtx: $rxPkg.Ctx.Data) => $injected2
      new $rxPkg.Var.Composed[$tType]($baseValue, $rxPkg.Rx.build { (owner,data) => $functionValue($baseValue, owner, data) })
    """

    resetExpr[Var[T]](c)(tree)
  }
}
