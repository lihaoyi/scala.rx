package rx

import org.scalatest._
import concurrent.Eventually

class ReadmeTests extends FreeSpec with Eventually {

  "Nesting" - {
    "WebPage" - {
      trait WebPage{
        val time = Var(System.currentTimeMillis())
        def update(): Unit  = time() = System.currentTimeMillis()
        val html: Rx[String]
      }
      class HomePage extends WebPage {
        val html = Rx{"Home Page! time: " + time()}
      }
      class AboutPage extends WebPage {
        val html = Rx{"About Me, time: " + time()}
      }

      val url = Var("www.mysite.com/home")
      val page = Rx{
        url() match{
          case "www.mysite.com/home" => new HomePage()
          case "www.mysite.com/about" => new AboutPage()
        }
      }

      println(page().html()) // Home Page! 1362000290775
      page().update()
      println(page().html()) // Home Page! 1362000291345
      url() = "www.mysite.com/about"
      println(page().html()) // About Me, 1362000294570
      page().update()
      println(page().html()) // About Me, 1362000299575
    }
  }
}
