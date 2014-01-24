package rx

import scala.concurrent.Future

package object ops {
  implicit def RxExtended[T](rx: Rx[Future[T]]) = new AsyncRx(rx)
}
