package rx

import util.{Try, Failure, Success}
import concurrent.{ExecutionContext, Future}

object Combinators{
  implicit class pimpedSig[T](source: Signal[T]){

    def skipFailures = filterSig(source)((oldTry, newTry) => newTry.isSuccess)

    def skipTry(predicate: (Try[T], Try[T]) => Boolean) = filterTry((x, y) => !predicate(x, y))
    def filterTry(predicate: (Try[T], Try[T]) => Boolean) = filterSig(source)(predicate)

    def skipDiff(successPred: (T, T) => Boolean = _!=_,
             failurePred: (Throwable, Throwable) => Boolean = _!=_) = {
      filterDiff(
        (x, y) => !successPred(x, y) ,
        (x, y) => !failurePred(x, y)
      )
    }
    def filter(successPred: T => Boolean, failurePred: Throwable => Boolean = x => true) = {
      new WrapSig[T, T](source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) if failurePred(thrown) => Failure(thrown)
          case (old, _) => old
        }
      )
    }
    def filterDiff(successPred: (T, T) => Boolean = _!=_,
               failurePred: (Throwable, Throwable) => Boolean = _!=_) = {

      filterSig[T](source)(
        (x, y) => (x, y) match {
          case (Success(a), Success(b)) => successPred(a, b)
          case (Failure(a), Failure(b)) => failurePred(a, b)
          case _ => true
        }
      )
    }

    def map[A](f: T => A) = new WrapSig[A, T](source)((x, y) => y.map(f))
  }
  implicit class pimpedFutureSig[T](source: Signal[Future[T]]){
    def async(default: T, target: T => Target[T] = AsyncCombinators.BaseTarget[T])(implicit executor: ExecutionContext) = {
      new AsyncSig("async " + source.name, default, source, target)
    }
  }

  def filterSig[T](source: Signal[T])(predicate: (Try[T], Try[T]) => Boolean) = {
    new WrapSig(source)((x: Try[T], y: Try[T]) => if (predicate(x, y)) y else x)
  }

  class WrapSig[T, A](source: Signal[A])(transformer: (Try[T], Try[A]) => Try[T])
  extends Signal[T]
  with Flow.Reactor[Any]{

    var lastResult = transformer(Failure(null), source.toTry)
    source.linkChild(this)

    def level = source.level + 1
    def name = "SkipFailure " + source.name
    def currentValue = lastResult.get
    def toTry = lastResult
    def getParents = Seq(source)
    def ping(incoming: Seq[Flow.Emitter[Any]]) = {
      val newTry = transformer(lastResult, source.toTry)
      if (newTry == toTry) Nil
      else {
        lastResult = newTry
        getChildren
      }
    }
  }


}

