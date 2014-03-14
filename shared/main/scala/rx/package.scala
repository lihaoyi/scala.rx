
/**
 * '''Scala.Rx''' is an experimental change propagation library for [[http://www.scala-lang.org/ Scala]].
 * Scala.Rx gives you Reactive variables ([[Rx]]s), which are smart variables who auto-update themselves
 * when the values they depend on change. The underlying implementation is push-based
 * [[http://en.wikipedia.org/wiki/Functional_reactive_programming FRP]] based on the
 * ideas in
 * [[http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf Deprecating the Observer Pattern]].
 *
 * A simple example which demonstrates its usage is:
 *
 * {{{
 * import rx._
 * val a = Var(1); val b = Var(2)
 * val c = Rx{ a() + b() }
 * println(c()) // 3
 * a() = 4
 * println(c()) // 6
 * }}}
 *
 * See [[https://github.com/lihaoyi/scala.rx the github page]] for more
 * instructions on how to use this package, or browse the classes on the left.
 */
package object rx {
  val Rx = core.Rx
  type Rx[+T] = core.Rx[T]

  val Obs = core.Obs
  type Obs = core.Obs

  val Var = core.Var
  type Var[T] = core.Var[T]

  implicit def StagedTuple[T](v: (Var[T], T)) = new core.Staged(v._1, v._2)
  implicit def StagedTupleSeq[T](v: Seq[(Var[T], T)]) = v.map(StagedTuple)
}

