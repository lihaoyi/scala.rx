package rx
import concurrent.duration._
import rx.ops.DomScheduler

/**
 * Created by haoyi on 1/25/14.
 */
object Main {
  def main(args: Array[String]): Unit = {
    implicit val s = new DomScheduler
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
    println("Hello World")

    org.scalajs.dom.setTimeout(() => println("WOO"), 100)
  }
}
