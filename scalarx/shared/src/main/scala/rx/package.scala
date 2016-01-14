import scala.util.Try

/**
 * Created by haoyi on 12/13/14.
 */
package object rx {


  trait GenericOps {

    /**
     * Filters out invalid values of this [[Node]] which fail the boolean
     * function `f`. Note that the initial (first) value of this [[Node]]
     * cannot be filtered out, even if it fails the check.
     */
    protected def filter0[T, In <: Node[T], Chk](in: In, next: () => Chk)(f: Chk => Boolean)(implicit ctx: RxCtx): Rx[T] = {
      var init = true
      lazy val ret: Rx[T] = Rx.build[T] { innerCtx: RxCtx =>
        in.Internal.addDownstream(innerCtx)
        val v = next()
        if (f(v) || init) {
          init = false
          in.now
        } else {
          ret()(innerCtx)
        }
      }(ctx)
      ret
    }

    /**
     * Creates a new [[Rx]] which depends on this one's value, transformed by `f`.
     */
    protected def map0[T, V, In <: Node[T]](in: In)(mapped: () => V)(implicit ctx: RxCtx): Rx[V] = {
      Rx.build { inner =>
        in.Internal.addDownstream(inner)
        mapped()
      }(ctx)
    }

    /**
     * Creates a new [[Rx]] which depends on this one's value, transformed by `f`.
     */
    protected def flatMap0[T, V, In <: Node[T]](in: In)(mapped: RxCtx => V)(implicit ctx: RxCtx): Rx[V] = {
      Rx.build { inner =>
        in.Internal.addDownstream(inner)
        mapped(inner)
      }(ctx)
    }


    /**
     * Given a `start` value, uses the current and subsequent values of this [[Rx]]
     * to transform the start value using `f`.
     */
    protected def fold0[T, V, In <: Node[T], ChkV](in: In, start: ChkV, next: ChkV => ChkV, output: ChkV => V)(implicit ctx: RxCtx): Rx[V] = {
      var prev = start
      Rx.build { inner =>
        in.Internal.addDownstream(inner)
        prev = next(prev)
        output(prev)
      }(ctx)
    }

    /**
     * Combines subsequent values of this [[Node]] using `f`
     */
    protected def reduce0[T, In <: Node[T], Chk](in: In, input: In => Chk, output: Chk => T)(next: Chk => Chk)(implicit ctx: RxCtx): Rx[T] = {
      var init = true
      var prev = input(in)
      Rx.build { innerCtx =>
        in.Internal.addDownstream(innerCtx)
        if (init) {
          init = false
          output(prev)
        } else {
          prev = next(prev)
          output(prev)
        }
      }(ctx)
    }

    /**
     * Creates an [[Obs]] that runs the given function with the value
     * of this [[Node]]
     */
    protected def foreach0[In <: Node[_]](in: In)(exec: => Unit) = {
      in.trigger(exec)
    }
  }

  class SafeOps[T](r: Rx[T]) extends GenericOps {

    def map[V](f: Try[T] => Try[V])(implicit ctx: RxCtx): Rx[V] =
      map0[T,V,Rx[T]](r) { () => f(r.toTry).get }

    def flatMap[V](f: Try[T] => Rx[V])(implicit ctx: RxCtx): Rx[V] = {
      flatMap0[T,V,Rx[T]](r) { inner =>
        val next = f(r.toTry)
        next()(inner)
      }
    }

    def filter(f: Try[T] => Boolean)(implicit ctx: RxCtx): Rx[T] =
      filter0[T,Rx[T],Try[T]](r,() => r.toTry)(f)

    def fold[V](start: Try[V])(f: ((Try[V], Try[T]) => Try[V]))(implicit ctx: RxCtx): Rx[V] =
      fold0[T,V,Rx[T],Try[V]](r, start, v => f(v,r.toTry), _.get)

    def reduce(f: (Try[T], Try[T]) => Try[T])(implicit ctx: RxCtx): Rx[T] =
      reduce0[T,Rx[T], Try[T]](r,_.toTry,_.get)(t => f(t,r.toTry))

    def foreach(f: T => Unit) = foreach0[Rx[T]](r)(r.toTry.foreach(f))
  }


  class NodeOps[T, N[T] <: Node[T]](n: N[T]) extends GenericOps {

    def map[V](f: T => V)(implicit ctx: RxCtx): Rx[V] = map0[T,V,N[T]](n) { () => f(n.now) }

    def flatMap[V](f: T => Rx[V])(implicit ctx: RxCtx): Rx[V] = {
      flatMap0[T,V,N[T]](n) { inner =>
        val next = f(n.now)
        next()(inner)
      }
    }

    def filter(f: T => Boolean)(implicit ctx: RxCtx): Rx[T] =
      filter0[T,N[T],T](n,() => n.now)(t => f(t))


    def fold[V](start: V)(f: ((V, T) => V))(implicit ctx: RxCtx): Rx[V] =
      fold0[T,V,N[T],V](n,start,v => f(v,n.now), v => v)


    def reduce(f: (T, T) => T)(implicit ctx: RxCtx): Rx[T] =
      reduce0[T,N[T],T](n,_.now,t=>t)(t => f(t,n.now))

    def foreach(f: T => Unit) = foreach0[N[T]](n)(f(n.now))
  }

  /**
   * All [[Node]]s have a set of operations you can perform on them, e.g. `map` or `filter`
   */
  implicit class NodePlus[T](n: Node[T]) extends NodeOps[T, Node](n)

  /**
   * All [[Rx]]s have a set of operations you can perform on them via `myRx.all.*`,
   * which lifts the operation to working on a `Try[T]` rather than plain `T`s
   */
  implicit class RxPlus[T](n: Rx[T]) {
    object all extends SafeOps[T](n)
  }

}