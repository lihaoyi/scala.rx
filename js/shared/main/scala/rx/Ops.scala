package rx

import scala.util.{Success, Try}


object Ops {
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
      def apply[T](v: Node[T]) = v()
    }
    object toTryMark extends GenericFunc[Rx, Try]{
      def apply[T](v: Rx[T]) = {
        v.mark()
        v.toTry
      }
    }
  }
  type Id[T] = T
  class GenericOps[M[_], N[_] <: Node[_], T]
                  (n: N[T], valFunc: GenericFunc[N, M], normFunc: GenericFunc[M, Id]){
    def filter(f: M[T] => Boolean): Rx[T] =  {
      lazy val ret: Rx[T] = Rx{
        val v = valFunc(n)
        if (f(v)) normFunc(v) else ret()
      }
      ret
    }
    def map[V](f: M[T] => M[V]) = Rx(normFunc(f(valFunc(n))))

    def fold[V](start: M[V])(f: (M[V], M[T]) => M[V]) = {
      var prev = start
      Rx{
        prev = f(prev, valFunc(n))
        normFunc(prev)
      }
    }
    def reduce(f: (M[T], M[T]) => M[T]) = {
      var init = true
      var prev = valFunc(n)
      Rx{
        if (init) {
          init = false
          normFunc(valFunc(n))
        } else {
          prev = f(prev, valFunc(n))
          normFunc(prev)
        }
      }
    }
    def foreach(f: M[T] => Unit) = {
      n.trigger(f(valFunc(n)))
    }
  }
  implicit class NodePlus[T](n: Node[T])
    extends GenericOps[Id, Node, T](n, GenericFunc.Apply, GenericFunc.Normal)

  implicit class RxPlus[T](n: Rx[T]){
    object all extends GenericOps[Try, Rx, T](n, GenericFunc.toTryMark, GenericFunc.Try)
  }
}
