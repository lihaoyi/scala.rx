import rx.opmacros.Operators
import Operators.Id
import rx.opmacros.Operators
import scala.util.Try

/**
 * Created by haoyi on 12/13/14.
 */
package object rx {



  /**
   * All [[Node]]s have a set of operations you can perform on them, e.g. `map` or `filter`
   */
  implicit class GenericOps[T](val node: Node[T]) extends AnyVal {
    import scala.language.experimental.macros

    def macroImpls = new Operators[T, Id] {
      def prefix = node
      def get[V](t: Node[V]) = t.now
      def unwrap[V](t: V) = t
    }
    def map[V](f: Id[T] => Id[V])(implicit owner: Ctx.Owner): Rx[V] = macro Operators.mapped[T, V, Id]

    def flatMap[V](f: Id[T] => Id[Rx[V]])(implicit owner: Ctx.Owner): Rx[V] = macro Operators.flatMapped[T, V, Id]

    def filter(f: T => Boolean)(implicit owner: Ctx.Owner): Rx[T] = macro Operators.filtered[T,T]

    def fold[V](start: V)(f: ((Id[V], Id[T]) => Id[V]))(implicit owner: Ctx.Owner): Rx[V] = macro Operators.folded[T, V, Id]

    def reduce(f: (Id[T], Id[T]) => Id[T])(implicit owner: Ctx.Owner): Rx[T] = macro Operators.reduced[T, Id]

    def foreach(f: T => Unit) = node.trigger(f(node.now))
  }

  abstract class SafeOps[T](val node: Rx[T]) {
    import scala.language.experimental.macros
    def macroImpls = new Operators[T, util.Try] {
      def prefix = node
      def get[V](t: Node[V]) = t.toTry
      def unwrap[V](t: Try[V]) = t.get
    }
    def map[V](f: Try[T] => Try[V])(implicit owner: Ctx.Owner): Rx[V] = macro Operators.mapped[T, V, Try]

    def flatMap[V](f: Try[T] => Try[Rx[V]])(implicit owner: Ctx.Owner): Rx[V] = macro Operators.flatMapped[T, V, Try]

    def filter(f: Try[T] => Boolean)(implicit owner: Ctx.Owner): Rx[T] = macro Operators.filtered[Try[T],T]

    def fold[V](start: Try[V])(f: (Try[V], Try[T]) => Try[V])(implicit owner: Ctx.Owner): Rx[V] = macro Operators.folded[T, V, Try]

    def reduce(f: (Try[T], Try[T]) => Try[T])(implicit owner: Ctx.Owner): Rx[T] = macro Operators.reduced[T, Try]

    def foreach(f: T => Unit) = node.trigger(node.toTry.foreach(f))
  }

  /**
   * All [[Rx]]s have a set of operations you can perform on them via `myRx.all.*`,
   * which lifts the operation to working on a `Try[T]` rather than plain `T`s
   */
  implicit class RxPlusOps[T](val r: Rx[T]) {
    object all extends SafeOps[T](r)
  }

}