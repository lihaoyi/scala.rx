import scala.util.Try

/**
 * Created by haoyi on 12/13/14.
 */
package object rx {
  object Internal{
    abstract class GenericFunc[M[_], N[_]]{
      def apply[T](v: M[T]): N[T]
    }
    object GenericFunc {
      object Normal extends GenericFunc[Id, Id]{
        def apply[T](v: Id[T]) = v
      }
      object Try extends GenericFunc[Try, Id]{
        def apply[T](v: Try[T]) = v.get
      }
      object Apply extends GenericFunc[Node, Id]{
        def apply[T](v: Node[T]) = v.now
      }
      object toTryMark extends GenericFunc[Rx, Try]{
        def apply[T](v: Rx[T]) = {
          v.toTry
        }
      }
    }
    type Id[T] = T
  }
  import Internal._

  /**
   * Operations that can take place on a [[Node]], in various arrangements
   */
  class GenericOps[M[_], N[_] <: Node[_], T]
  (n: N[T], valFunc: GenericFunc[N, M], normFunc: GenericFunc[M, Id]){
    /**
     * Filters out invalid values of this [[Node]] which fail the boolean
     * function `f`. Note that the initial (first) value of this [[Node]]
     * cannot be filtered out, even if it fails the check.
     */
    def filter(f: M[T] => Boolean): Rx[T] =  {
      var init = true
      lazy val ret: Rx[T] = Rx.build[T]{ implicit ctx =>
        n.Internal.addDownstream(ctx)
        val v = valFunc(n)
        if (f(v) || init) {
          init = false
          normFunc(v)
        } else {
          ret()
        }
      }
      ret
    }

    /**
     * Creates a new [[Rx]] which depends on this one's value, transformed by `f`.
     */
    def map[V](f: M[T] => M[V]) = Rx.build { implicit ctx =>
      n.Internal.addDownstream(ctx)
      normFunc(f(valFunc(n)))
    }

    /**
     * Given a `start` value, uses the current and subsequent values of this [[Rx]]
     * to transform the start value using `f`.
     */
    def fold[V](start: M[V])(f: (M[V], M[T]) => M[V]) = {
      var prev = start
      Rx.build{ implicit ctx =>
        prev = f(prev, valFunc(n))
        normFunc(prev)
      }
    }

    /**
     * Combines subsequent values of this [[Node]] using `f`
     */
    def reduce(f: (M[T], M[T]) => M[T]) = {
      var init = true
      var prev = valFunc(n)
      Rx.build{ implicit ctx =>
        n.Internal.addDownstream(ctx)
        if (init) {
          init = false
          normFunc(valFunc(n))
        } else {
          prev = f(prev, valFunc(n))
          normFunc(prev)
        }
      }
    }

    /**
     * Creates an [[Obs]] that runs the given function with the value
     * of this [[Node]]
     */
    def foreach(f: M[T] => Unit) = {
      n.trigger(f(valFunc(n)))
    }
  }

  /**
   * All [[Node]]s have a set of operations you can perform on them, e.g. `map` or `filter`
   */
  implicit class NodePlus[T](n: Node[T])
    extends GenericOps[Id, Node, T](n, GenericFunc.Apply, GenericFunc.Normal)
  /**
   * All [[Rx]]s have a set of operations you can perform on them via `myRx.all.*`,
   * which lifts the operation to working on a `Try[T]` rather than plain `T`s
   */
  implicit class RxPlus[T](n: Rx[T]){
    object all extends GenericOps[Try, Rx, T](n, GenericFunc.toTryMark, GenericFunc.Try)
  }
}
