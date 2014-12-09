package rx

import scala.util.{Success, Try}


object Ops {
  implicit class NodePlus[T](n: Node[T]){
    def foreach(f: T => Unit) = {
      n.trigger(f(n()))
    }
    def map[V](f: T => V) = {
      Rx(f(n()))
    }

    def filter(f: T => Boolean) = {
      lazy val ret: Rx[T] = Rx{
        val v = n()
        if (f(v)) v else ret()
      }
      ret
    }

    def reduce(f: (T, T) => T) = {
      var init = true
      lazy val ret: Rx[T] = Rx{
        if (init) {
          init = false
          n()
        } else f(ret(), n())
      }
      ret
    }
  }
  implicit class RxPlus[T](n: Rx[T]){
    def mapAll[V](f: Try[T] => Try[V]) = {
      lazy val ret: Rx[V] = Rx{
        n.mark()
        f(n.toTry).get
      }
      n.downStream.add(ret)
      ret
    }
    def filterAll(f: Try[T] => Boolean) = {
      lazy val ret: Rx[T] = Rx{
        val v = n.toTry
        n.mark()
        println("ZZZ " + v + " " + f(v))
        if (f(v)) v.get else ret()
      }
      ret
    }
    def reduceAll(f: (Try[T], Try[T]) => Try[T]) = {
      var init = true
      lazy val ret: Rx[T] = Rx{
        if (init) {
          init = false
          n()
        } else {
          n.mark()
          f(ret.toTry, n.toTry).get
        }
      }
      ret
    }
  }
}
