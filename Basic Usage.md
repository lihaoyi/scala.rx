Basic Use
=========
```scala
import rx._
val a = Var(1); val b = Var(2)
val c = Rx{ a() + b() }
println(c()) // 3
a() = 4
println(c()) // 6
```

The above example is an executable program. In general, `import rx._` is enough to get you started with Scala.Rx, and it will be assumed in all further examples. These examples are all taken from the unit tests.

The basic entities you have to care about are `Var`, `Rx` and `Obs`:

- `Var`: a smart variable which you can get using `a()` and set using `a() = ...`. Whenever its value changes, it notifies any downstream entity which needs to be recalculated.
- `Rx`: a reactive definition which automatically captures any `Var`s or other `Rx`s which get called in its body, flagging them as dependencies and re-calculating whenever one of them changes. Like a `Var`, you can use the `a()` syntax to retrieve its value, and it also notifies downstream entities when the value changes.
- `Obs`: an observer on one or more `Var` s or `Rx` s, performing some side-effect when the observed node changes entity.

Using these components, you can easily construct a *dataflow graph*, and have the various values within the dataflow graph be kept up to date when the inputs to the graph change:

```scala
val a = Var(1) // 1

val b = Var(2) // 2

val c = Rx{ a() + b() } // 3
val d = Rx{ c() * 5 } // 15
val e = Rx{ c() + 4 } // 7
val f = Rx{ d() + e() + 4 } // 26

println(f()) // 26
a() = 3
println(f()) // 38
```

The dataflow graph for this program looks like this:

![Dataflow Graph](https://github.com/lihaoyi/scala.rx/blob/master/media/Intro.png?raw=true)

Where the `Var`s are represented by squares, the `Rx`s by circles and the dependencies by arrows. Each `Rx` is labelled with its name, its body and its value.

Modifying the value of `a` causes the changes the propagate through the dataflow graph

![Dataflow Graph](https://github.com/lihaoyi/scala.rx/blob/master/media/IntroProp.png?raw=true)

As can be seen above, changing the value of `a` causes the change to propagate all the way through `c` `d` `e` to `f`. You can use a `Var` and `Rx` anywhere you an use a normal variable.

The changes propagate through the dataflow graph in *waves*. Each update to a `Var` touches off a propagation wave, which pushes the changes from that `Var` to any `Rx` which is (directly or indirectly) dependent on its value. In the process, it is possible for a `Rx` to be re-calculated more than once.

Observers
=========
As mentioned, `Obs` s can be used to observe `Rx` s and `Var` s and perform side effects when they change:

```scala
val a = Var(1)
var count = 0
val o = Obs(a){
count = a() + 1
}
assert(count === 2)
a() = 4
assert(count === 5)
```

This creates a dataflow graph that looks like:

![Dataflow Graph](https://github.com/lihaoyi/scala.rx/blob/master/media/Observer.png?raw=true)

When `a` is modified, the observer `o` will perform the side effect:

![Dataflow Graph](https://github.com/lihaoyi/scala.rx/blob/master/media/Observer2.png?raw=true)

The body of `Rx`s should be side effect free, as they may be run more than once per propagation wave. You should use `Obs`s to perform your side effects, as they are guaranteed to run only once per propagation wave after the values for all `Rx`s have stabilized.

Scala.Rx provides a convenient `.foreach()` combinator, which provides an alternate way of creating an `Obs` from an `Rx`:

```scala
val a = Var(1)
var count = 0
val o = a.foreach{ x =>
  count = x + 1
}
assert(count === 2)
a() = 4
assert(count === 5)
```

This example does the same thing as the code above.

Note that the body of the `Obs` is run once initially when it is declared. This matches the way each `Rx` is calculated once when it is initially declared. but it is conceivable that you want an `Obs` which first for the first time only when the `Rx` it is listening to *changes*. You can do this by passing the `skipInitial` flag when creating it:

```scala
val a = Var(1)
var count = 0
val o = Obs(a, skipInitial=true){
  count = count + 1
}
assert(count === 0)
a() = 2
assert(count === 1)
```

An `Obs` acts to encapsulate the callback that it runs. They can be passed around, stored in variables, etc.. When the `Obs` gets garbage collected, the callback will stop triggering. Thus, an `Obs` should be stored in the object it affects: if the callback only affects that object, it doesn't matter when the `Obs` itself gets garbage collected, as it will only happen after that object holding it becomes unreachable, in which case its effects cannot be observed anyway. An `Obs` can also be actively shut off, if a stronger guarantee is needed:

```scala

val a = Var(1)
val b = Rx{ 2 * a() }
var target = 0
val o = Obs(b){
  target = b()
}
assert(target === 2)
a() = 2
assert(target === 4)
o.active = false
a() = 3
assert(target === 4)
```

After manually setting `active = false`, the `Obs` no longer triggers.

In general, Scala.Rx revolves around constructing dataflow graphs which automatically keep things in sync, which you can easily interact with from external, imperative code. This involves using:

- `Var`s as inputs to the dataflow graph from the imperative world
- `Rx`s as the intermediate nodes in the dataflow graphs
- `Obs`s as the output from the dataflow graph to the imperative world

Complex Reactives
-----------------
`Rx`s are not limited to `Ints`. `String`s, `Seq[Int]`, `Seq[String]`, anything can go inside an `Rx`:

```scala
val a = Var(Seq(1, 2, 3))
val b = Var(3)
val c = Rx{ b() +: a() }
val d = Rx{ c().map("omg" * _) }
val e = Var("wtf")
val f = Rx{ (d() :+ e()).mkString }

assert(f() === "omgomgomgomgomgomgomgomgomgwtf")
a() = Nil
assert(f() === "omgomgomgwtf")
e() = "wtfbbq"
assert(f() === "omgomgomgwtfbbq")
```

As shown, you can use Scala.Rx's reactive variables to model problems of arbitrary complexity, not just trivial ones which involve primitive numbers.

Error Handling
--------------
Since the body of an `Rx` can be any arbitrary Scala code, it can throw exceptions. Propagating the exception up the call stack would not make much sense, as the code evaluating the `Rx` is probably not in control of the reason it failed. Instead, any exceptions are caught by the `Rx` itself and stored internally as a `Try`.

This can be seen in the following unit test:

```scala
val a = Var(1)
val b = Rx{ 1 / a() }
assert(b() === 1)
assert(b.toTry === Success(1))
a() = 0
intercept[ArithmeticException]{
  b()
}
inside(b.toTry){ case Failure(_) => () }
```

Initially, the value of `a` is `1` and so the value of `b` also is `1`. You can also extract the internal `Try` using `b.toTry`, which at first is `Success(1)`.

However, when the value of `a` becomes `0`, the body of `b` throws an `ArithmeticException`. This is caught by `b` and re-thrown if you try to extract the value from `b` using `b()`. You can extract the entire `Try` using `toTry` and pattern match on it to handle both the `Success` case as well as the `Failure` case.

When you have many `Rx`s chained together, exceptions propagate forward following the dependency graph, as you would expect. The following code:

```scala
val a = Var(1)
val b = Var(2)

val c = Rx{ a() / b() }
val d = Rx{ a() * 5 }
val e = Rx{ 5 / b() }
val f = Rx{ a() + b() + 2 }
val g = Rx{ f() + c() }

inside(c.toTry){case Success(0) => () }
inside(d.toTry){case Success(5) => () }
inside(e.toTry){case Success(2) => () }
inside(f.toTry){case Success(5) => () }
inside(g.toTry){case Success(5) => () }

b() = 0

inside(c.toTry){case Failure(_) => () }
inside(d.toTry){case Success(5) => () }
inside(e.toTry){case Failure(_) => () }
inside(f.toTry){case Success(3) => () }
inside(g.toTry){case Failure(_) => () }
```

Creates a dependency graph that looks like the follows:

![Dataflow Graph](https://github.com/lihaoyi/scala.rx/blob/master/media/Errors.png?raw=true)

In this example, initially all the values for `a`, `b`, `c`, `d`, `e`, `f` and `g` are well defined. However, when `b` is set to `0`:

![Dataflow Graph](https://github.com/lihaoyi/scala.rx/blob/master/media/Errors2.png?raw=true)

`c` and `e` both result in exceptions, and the exception from `c` propagates to `g`. Attempting to extract the value from `g` using `g()`, for example, will re-throw the ArithmeticException. Again, using `toTry` works too.

Nesting
-------
`Rx`s can contain other `Rx`s, arbitrarily deeply. This example shows the `Rx`s nested two levels deep:

```scala
val a = Var(1)
val b = Rx{
    (Rx{ a() }, Rx{ math.random })
}
val r = b()._2()
a() = 2
assert(b()._2() === r)
```

In this example, we can see that although we modified `a`, this only affects the left-inner `Rx`, neither the right-inner `Rx` (which takes on a different, random value each time it gets re-calculated) or the outer `Rx` (which would cause the whole thing to re-calculate) are affected. A slightly less contrived example may be:

```scala
var fakeTime = 123
trait WebPage{
    def fTime = fakeTime
    val time = Var(fTime)
    def update(): Unit  = time() = fTime
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

assert(page().html() === "Home Page! time: 123")

fakeTime = 234
page().update()
assert(page().html() === "Home Page! time: 234")

fakeTime = 345
url() = "www.mysite.com/about"
assert(page().html() === "About Me, time: 345")

fakeTime = 456
page().update()
assert(page().html() === "About Me, time: 456")
```

In this case, we define a web page which has a `html` value (a `Rx[String]`). However, depending on the `url`, it could be either a `HomePage` or an `AboutPage`, and so our `page` object is a `Rx[WebPage]`.

Having a `Rx[WebPage]`, where the `WebPage` has an `Rx[String]` inside, seems natural and obvious, and Scala.Rx lets you do it simply and naturally. This kind of objects-within-objects situation arises very naturally when modelling a problem in an object-oriented way. The ability of Scala.Rx to gracefully handle the corresponding `Rx`s within `Rx`s allows it to gracefully fit into this paradigm, something I found lacking in most of the [[Related-Work]] I surveyed.

Most of the examples here are taken from the [unit tests](https://github.com/lihaoyi/scala.rx/blob/master/src/test/scala/rx/BasicTests.scala), which provide more examples on guidance on how to use this library.

**Next Section: [Combinators](https://github.com/lihaoyi/scala.rx/wiki/Combinators)**
