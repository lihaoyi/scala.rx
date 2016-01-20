package rx

/**
  * Created by haoyi on 1/19/16.
  */
object Main {
  def main(args: Array[String]): Unit = {
    import Ctx.Owner.Unsafe._
    val a = Var(1)
    val b = Rx{ a() + 1 }

  }
}
