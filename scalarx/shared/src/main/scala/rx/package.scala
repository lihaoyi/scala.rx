import monocle.Lens
import rx.opmacros.Operators
import rx.opmacros.Utils.Id

import scala.util.Try

/**
 * Created by haoyi on 12/13/14.
 */
package object rx {


  object GenericRxOps{

    class Macros[T](node: Rx[T]) extends Operators[T, Id] {
      def prefix = node
      def get[V](t: Rx[V]) = t.now
      def unwrap[V](t: V) = t
    }
  }
  /**
   * All [[Rx]]s have a set of operations you can perform on them, e.g. `map` or `filter`
   */
  implicit class GenericRxOps[T](val node: Rx[T]) extends AnyVal {
    import scala.language.experimental.macros

    def macroImpls = new GenericRxOps.Macros(node)

    def map[V](f: Id[T] => Id[V])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[V] = macro Operators.map[T, V, Id]

    def flatMap[V](f: Id[T] => Id[Rx[V]])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[V] = macro Operators.flatMap[T, V, Id]

    def filter(f: Id[T] => Boolean)(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[T] = macro Operators.filter[T,T]

    def fold[V](start: Id[V])(f: ((Id[V], Id[T]) => Id[V]))(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[V] = macro Operators.fold[T, V, Id]

    def reduce(f: (Id[T], Id[T]) => Id[T])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[T] = macro Operators.reduce[T, Id]

    def foreach(f: T => Unit)(implicit ownerCtx: Ctx.Owner): Obs = node.trigger(f(node.now))
  }

  implicit class GenericVarOps[T](val base: Var[T]) extends AnyVal {
    def imap[V](read: Id[T] => Id[V])(write: Id[V] => Id[T])(implicit ownerCtx: Ctx.Owner): Var[V] = new Var.Isomorphic[T,V](base,read,write)

    def zoom[V](read: Id[T] => Id[V])(write: (Id[T],Id[V]) => Id[T])(implicit ownerCtx: Ctx.Owner): Var[V] = new Var.Zoomed[T,V](base,read,write)
    def zoom[V](lens: Lens[T,V])(implicit ownerCtx: Ctx.Owner): Var[V] = new Var.Zoomed[T,V](base,lens.get,(base,zoomed) => lens.set(zoomed)(base))

    def mapRead(read: Var[T] => T)(implicit ownerCtx: Ctx.Owner): Var[T] = macro rx.opmacros.MapReadMacro.impl[T]
  }

  object SafeRxOps{
    class Macros[T](node: Rx[T]) extends Operators[T, util.Try] {
      def prefix = node
      def get[V](t: Rx[V]) = t.toTry
      def unwrap[V](t: Try[V]) = t.get
    }
  }

  abstract class SafeRxOps[T](val node: Rx[T]) {
    import scala.language.experimental.macros
    def macroImpls = new SafeRxOps.Macros(node)

    def map[V](f: Try[T] => Try[V])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[V] = macro Operators.map[T, V, Try]

    def flatMap[V](f: Try[T] => Try[Rx[V]])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[V] = macro Operators.flatMap[T, V, Try]

    def filter(f: Try[T] => Boolean)(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[T] = macro Operators.filter[Try[T],T]

    def fold[V](start: Try[V])(f: (Try[V], Try[T]) => Try[V])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[V] = macro Operators.fold[T, V, Try]

    def reduce(f: (Try[T], Try[T]) => Try[T])(implicit ownerCtx: Ctx.Owner): Rx.Dynamic[T] = macro Operators.reduce[T, Try]

    def foreach(f: T => Unit)(implicit ownerCtx: Ctx.Owner): Obs = node.trigger(node.toTry.foreach(f))
  }

  /**
   * All [[Rx]]s have a set of operations you can perform on them via `myRx.all.*`,
   * which lifts the operation to working on a `Try[T]` rather than plain `T`s
   */
  implicit class RxPlusOps[T](val r: Rx[T]) {
    object all extends SafeRxOps[T](r)
  }

}