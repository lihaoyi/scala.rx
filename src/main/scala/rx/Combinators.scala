package rx

import util.{Try, Failure, Success}

object Combinators{
  implicit class pimpedSig[T](source: Signal[T]){

    def skipFailures = new FilterSig(source)((oldTry, newTry) => newTry.isSuccess)

    def skipTry(predicate: (Try[T], Try[T]) => Boolean) = filterTry((x, y) => !predicate(x, y))
    def filterTry(predicate: (Try[T], Try[T]) => Boolean) = new FilterSig(source)(predicate)

    def skip(successPred: (T, T) => Boolean = _!=_,
             failurePred: (Throwable, Throwable) => Boolean = _!=_) = {
      filter(
        (x, y) => !successPred(x, y) ,
        (x, y) => !failurePred(x, y)
      )
    }
    def filter(successPred: (T, T) => Boolean = _!=_,
               failurePred: (Throwable, Throwable) => Boolean = _!=_) = {

      new FilterSig[T](source)(
        (x, y) => (x, y) match {
          case (Success(a), Success(b)) => successPred(a, b)
          case (Failure(a), Failure(b)) => failurePred(a, b)
          case _ => true
        }
      )
    }

    def map[A](f: T => A) = new WrapSig[A, T](source)((x, y) => y.map(f))
  }

  class FilterSig[T](source: Signal[T])
                    (predicate: (Try[T], Try[T]) => Boolean)
  extends WrapSig(source)((x: Try[T], y: Try[T]) => if (predicate(x, y)) y else x)

  class WrapSig[T, A](source: Signal[A])(transformer: (Try[T], Try[A]) => Try[T])
  extends Signal[T]
  with Reactor[Any]{

    var lastResult = transformer(Failure(null), source.toTry)
    source.linkChild(this)

    def level = source.level + 1
    def name = "SkipFailure " + source.name
    def currentValue = lastResult.get
    def toTry = lastResult
    def getParents = Seq(source)
    def ping(incoming: Seq[Emitter[Any]]) = {
      val newTry = transformer(lastResult, source.toTry)
      if (newTry == toTry) Nil
      else {
        lastResult = newTry
        getChildren
      }
    }
  }
}
object NoHistoryException extends Exception()
