Scala.Rx
========

Scala.Rx is an experimental change propagation library for [Scala](http://www.scala-lang.org/). Scala.Rx gives you Reactive variables (`Rx`s), which are smart variables who auto-update themselves when the values they depend on change. The underlying implementation is push-based [FRP](http://en.wikipedia.org/wiki/Functional_reactive_programming) based on the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf).

A simple example which demonstrates the behavior is:

```scala
import rx._
val a = Var(1); val b = Var(2)
val c = Rx{ a() + b() }
println(c()) // 3
a() = 4
println(c()) // 6
```

The idea being that 99% of the time, when you re-calculate a variable, you re-calculate it the same way you initially calculated it. Furthermore, you only re-calculate it when one of the values it depends on changes. Scala.Rx does this for you automatically, and handles all the tedious update logic for you so you can focus on other, more interesting things!

Basic Use
=========
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

As can be seen above, changing the value of `a` causes the change to propagate all the way through `c` `d` `e` to `f`. You can use a `Var` and `Rx` anywhere you an use a normal variable.

The changes propagate through the dataflow graph in *waves*. Each update to a `Var` touches off a propagation wave, which pushes the changes from that `Var` to any `Rx` which is (directly or indirectly) dependent on its value. In the process, it is possible for a `Rx` to be re-calculated more than once.

As mentioned, `Obs` s can be used to observe `Rx` s and `Var` s and perform side effects when they change:

```scala
val a = Var(1)
var count = 0
val o = Obs(a){
    count = count + 1
}
a() = 2
assert(count === 1)
```

The body of `Rx`s should be side effect free, as they may be run more than once per propagation wave. You should use `Obs`s to perform your side effects, as they are guaranteed to run only once per propagation wave after the values for all `Rx`s have stabilized.

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

When you have many `Rx`s chained together, exceptions propagate forward following the dependency graph, as you would expect:

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

In this example, initially all the values for `a`, `b`, `c`, `d`, `e`, `f` and `g` are well defined. However, when `b` is set to `0`, `c` and `e` both result in exceptions, and the exception from `c` propagates to `g`. Attempting to extract the value from `g` using `g()`, for example, will re-throw the ArithmeticException. Again, using `toTry` works too.

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

Having a `Rx[WebPage]`, where the `WebPage` has an `Rx[String]` inside, seems natural and obvious, and Scala.Rx lets you do it simply and naturally. This kind of objects-within-objects situation arises very naturally when modelling a problem in an object-oriented way. The ability of Scala.Rx to gracefully handle the corresponding `Rx`s within `Rx`s allows it to gracefully fit into this paradigm, something I found lacking in most of the [alternatives](#related-work) I surveyed.

Basic Combinators
-----------------
Scala.Rx also provides a set of combinators which allow your to easily transform your `Rx`s. The three basic combinators: `map()`, `filer()` and `reduce()` are modelled after the scala collections library, and provide an easy way of transforming the values coming out of a `Rx`.

###Map
```scala
val a = Var(10)
val b = Rx{ a() + 2 }
val c = a.map(_*2)
val d = b.map(_+3)
assert(c() === 20)
assert(d() === 15)
a() = 1
assert(c() === 2)
assert(d() === 6)
```

`map` does what you would expect, creating a new `Rx` with the value of the old `Rx` transformed by some function. For example, `a.map(_*2)` is essentially equivalent to `Rx{ a() * 2 }`, but somewhat more convenient to write.

###Filter
```scala
val a = Var(10)
val b = a.filter(_ > 5)
a() = 1
assert(b() === 10)
a() = 6
assert(b() === 6)
a() = 2
assert(b() === 6)
a() = 19
assert(b() === 19)
```

`filter` ignores changes to the value of the `Rx` that fail the predicate.

Note that none of the `filter` methods is able to filter out the first, initial value of a `Rx`, as there is no "older" value to fall back to. Hence this:

```scala
val a = Var(2)
val b = a.filter(_ > 5)
println(b())
```

will print out "2".

###Reduce
```scala
val a = Var(1)
val b = a.reduce(_ * _)
a() = 2
assert(b() === 2)
a() = 3
assert(b() === 6)
a() = 4
assert(b() === 24)
```

The `reduce` operator combines subsequent values of an `Rx` together, starting from the initial value. Every change to the original `Rx` is combined with the previously-stored value and becomes the new value of the reduced `Rx`.

Each of these three combinators has a counterpart in `mapAll()`, `filterAll()` and `reduceAll()` which operates on `Try[T]`s rather than `T`s, in the case where you need the added flexibility to handle `Failure`s in some special way.

Advanced Combinators
--------------------
These are combinators which do more than simply transforming a value from one to another. Many of them have asynchronous effects, and can spontaneously modify the dataflow graph and begin propagation waves without any external trigger.

###Async

```scala
val p = Promise[Int]()
val a = Rx{
    p.future
}.async(10)
assert(a() === 10)
p.complete(scala.util.Success(5))
eventually {
    assert(a() === 5)
}
```

The `async` conbinator only applies to `Rx[Future[_]]`s. It takes an initial value, which will be the value of the `Rx` until the `Future` completes, at which point the the value will become the value of the `Future`.

`async` can create `Future`s as many times as necessary. This example shows it creating two distinct `Future`s:

```scala
var p = Promise[Int]()
val a = Var(1)
val b = Rx{
    val A = a()
    p.future.map{_ + A}
}.async(10)
assert(b() === 10)
p.complete(scala.util.Success(5))
eventually{
    assert(b() === 6)
}

p = Promise[Int]()
a() = 2
assert(b() === 6)
p.complete(scala.util.Success(7))
eventually{
    assert(b() === 9)
}
```

The value of `b()` updates as you would expect as the series of `Future`s complete (in this case, manually using `Promise`s).

This is handy if your dependency graph contains some asynchronous elements. For example, you could have a `Rx` which depends on another `Rx`, but requires an asynchronous web request to calculate its final value. With `async`, the results from the asynchronous web request will be pushed back into the change propagation graph automatically when the `Future` completes, starting off another propagation run and conveniently updating the rest of the graph which depends on the new result.

`async` optionally takes a second argument which causes out-of-order `Future`s to be dropped. This is useful if you always want to have the result of the most recently-created `Future` which completed, rather than the most-recently-completed `Future`.

###Timer

```scala
val t = Timer(100 millis)
var count = 0
val o = Obs(t){
    count = count + 1
}

for(i <- 0 to 5){
    eventually{ assert(t() == i) }
}

assert(count >= 5)
```

A `Timer` is a `Rx` that generates events on a regular basis. The events are based on the `scheduler` of the implicit `ActorSystem`, which defaults to a maximum precision of about 100 milliseconds. In the example above, the for-loop checks that the value of the timer `t()` increases over time from 0 to 5, and then checks that `count` has been incremented at least that many times.


The scheduled task is cancelled automatically when the `Timer` object becomes unreachable, so it can be garbage collected. This means you do not have to worry about managing the life-cycle of the `Timer`.

###Delay
```scala
val a = Var(10)
val b = a.delay(250 millis)

a() = 5
assert(b() === 10)
eventually{
  assert(b() === 5)
}

a() = 4
assert(b() === 5)
eventually{
  assert(b() === 4)
}
```

The `delay(t)` combinator creates a delayed version of an `Rx` whose value lags the original by a duration `t`. When the `Rx` changes, the delayed version will not change until the delay `t` has passed.

This example shows the delay being applied to a `Var`, but it could easily be applied to an `Rx` as well.

###Debounce
```scala
val a = Var(10)
val b = a.debounce(200 millis)
a() = 5
assert(b() === 5)

a() = 2
assert(b() === 5)

eventually{
  assert(b() === 2)
}

a() = 1
assert(b() === 2)

eventually{
  assert(b() === 1)
}
```

The `debounce(t)` combinator creates a version of an `Rx` which will not update more than once every time period `t`.

If multiple updates happen with a short span of time (less than `t` apart), only the first update will take place immediately, and a second update will take place after the time `t` has passed.

How it Works
============
You have by now seen how Scala.Rx works, from the point of view of a user of the API. This section will elaborate on the underlying implementation.  

Dependency Tracking
-------------------
Scala.Rx tracks the dependency graph between different `Var`s and `Rx`s without any explicit annoation by the programmer. This means that in (almost) all cases, you can just write your code as if it wasn't being tracked, and Scala.Rx would build up the dependency graph automatically.

Every time the body of an `Rx{...}` is evaluated (or re-evaluated), it is put into a `DynamicVariable`. Any calls to the `.apply()` methods of other `Rx`s then inspect this `DynamicVariable` to determine who (if any) is the `Rx` being evaluated. This is linked up with the `Rx` whose `.apply()` is being called, creating a dependency between them. Thus a dependency graph is implicitly created without any action on the part of the programmer.

The dependency-tracking strategy of Scala.Rx is based of a subset of the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), in particular their definition of "Opaque Signals". The implementation follows it reasonably closely.

Propagation
-----------

###Forward References
Once we have evaluated our `Var`s and `Rx`s once and have a dependency graph, how do we keep track of our children (the `Rx`s who depend on us) and tell them to update? Simply keeping a `List[]` of all children will cause memory leaks, as the `List[]` will prevent any child from being garbage collected even if all other references to the child have been lost and the child is otherwise unaccessable.

Instead, Scala.Rx using a list of `WeakReference`s. These allow the `Rx` to keep track of its children while still letting them get garbage collected when all other references to them are lost. When a child becomes unreachable and gets garbage collected, the `WeakReference` becomes `null`, and these null references get cleared from the list every time it is updated.

###Propagation Strategies
The default propagation of changes is done in a breadth-first, topologically-sorted order, similar to that described in the paper. Each propagation run occurs when a `Var` is set, e.g. in
 
```scala
val x = Var(0)
val y = Rx(x * 2)
println(y) // 2

x() = 2
println(y) // 4
```

The propagation begins when `x` is modified via `x() = 2`, in this case ending at `y` which updates to the new value `4`.

Nodes earlier in the dependency graph are evaluated before those down the line. However, due to the fact that the dependencies of a `Rx` are not known until it is evaluated, it is impossible to strictly maintain this invariant at all times, since the underlying graph could change unpredictably.

In general, Scala.Rx keeps track of the topological order dynamically, such that after initialization, if the dependency graph does not change too radically, most nodes *should* be evaluated only once per propagation, but this is not a hard guarantee.

Hence, it is possible that an `Rx` will get evaluated more than once, even if only a single `Var` is updated. You should ensure that the body of any `Rx`s can tolerate being run more than once without harm. If you need to perform side effects, use an `Obs`, which only executes its side effects once per propagation run after the values for all `Rx`s have stabilized.

The default propagation does this all synchronously: it performs each update one at a time, and the `update` function

```scala
x() = 2
```

only returns after all updates have completed. This can be changed by creating a new `BreadthFirstPropagator` with a custom `ExecutionContext`. e.g.:

```scala
implicit val propagator = new BreadthFirstPropagator(ExecutionContext.global)

x() = 2
```

In which case the propagation will be done in parallel, according to the global `ExecutionContext`. 

Even with a custom `ExecutionContext`, all updates occur in (roughly) topologically sorted order. If for some reason you do not want this, it is possible to customize this by creating a custom `Propagator` who is responsible for performing these updates. The `Propagator` trait is defined as:

```scala
trait Propagator{
  def propagate(nodes: Seq[(Flow.Emitter[Any], Flow.Reactor[Nothing])]): Future[Unit]
  implicit def executionContext: ExecutionContext
}
```

Where `propagate` method takes a `Seq` of updates that must happen: every propagation run, there is a set of `Emitter`s telling `Reactor`s to update. Now you can have the propagation happen in any order you want

Concurrency and Asynchrony
--------------------------
By default, everything happens on a single-threaded execution context and there is no parallelism. By using a custom [ExecutionContext](http://www.scala-lang.org/archives/downloads/distrib/files/nightly/docs/library/index.html#scala.concurrent.ExecutionContext), it is possible to have the updates in each propagation run happen in parallel. For more information on ExecutionContexts, see the [Akka Documentation](http://doc.akka.io/docs/akka/2.1.2/scala/futures.html#futures-scala). The unit tests also contain [an example](https://github.com/lihaoyi/scala.rx/blob/master/src/test/scala/rx/AdvancedTests.scala#L171) of a dependency graph whose evaluation is spread over multiple threads in this way.

Even without using an explicitly parallelising ExecutionContext, parallelism could creep into your code in subtle ways: a delayed `Rx`, for example may happen to fire and continue its propagation just as you update a `Var` somewhere on the same dependency graph, resulting in two propagation waves proceeding in parallel.

In general, the rules for parallel execution of an individual node in the dependency graph is as follows:

- A `Rx` can up updated by an arbitrarily high number of parallel threads: *make sure your code can handle this!*. I will refer to each of these as a *parallel update*.
- A parallel update **U** gets committed (i.e. its result becomes the value of the `Rx` after completion) *if and only if* the time at which **U**'s computation began (its *start time*) is greater than the start time of the last-committed parallel update

This policy is implemented using the __java.util.concurrent.Atomic*__ classes. The final compare-start-times-and-replace-if-timestamp-is-greater action is implemented as a STM-style retry-loop on an __AtomicReference__. This approach is [lock free](http://en.wikipedia.org/wiki/Non-blocking_algorithm#Lock-freedom), and since the time required to compute the result is probably far greater than the time spent trying to commit it, the number of retries should in practice be minimal.

###Weak-References

The weak-forward-references to an `Rx` from its dependencies is unusual in that unlike the rest of the state regarding the `Rx`, it is not kept within the `Rx` itself! Rather, it is kept within its parents. Hence updates to these weak references cannot conveniently be seralized by encapsulating the state within that `Rx`'s state.

Instead, Scala.Rx does two things:

- Make the list of `WeakReferences` append-only
- Handle appends to the list via an atomic [CAS](http://en.wikipedia.org/wiki/Compare-and-swap), with a re-try if there is a conflict.
- Maintains a list of Parents in each Child, in addition to having a list of Children in each Parent. This list of parents will then be kept up to date, and updates to it will be serialized when the `Rx`'s Agent updates.

As a result, although the _forward_ references from parent to child may not always be kept up to date, they will always form a super-set of the "correct" relationships. These "correct" relationships will be kept up to date in the _backward_ references from child to parent, and will ensure that things behave correctly even if the set of forward references is larger than it needs to be. The atomic-CAS-with-re-try provides an elegant, lock-free mechanism by which forward references can be appended to this list.

Related Work
============
Cool things do not happen in a vacuum, and Scala.Rx borrows ideas and inspiration from a range of existing projects.

Scala.React
-----------
Scala.React, as described in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), contains the reactive change propagation portion (there called `Signal`s) which is similar to what Scala.Rx does. However, it does much more than that with its event-streams and multiple DSLs using delimited continuations to do fancy things.

However, I found it a pain to set up, requiring a bunch of global configuration, with a complex, global "engine", even running its own thread pools. This made it extremely difficult to reason about interactions between parts of my programs: would completely-separate dataflow graphs be able to affect each other through this global-mutable engine? Would the performance of multithreaded code start to slow down as the number of threads rises, as the engine becomes a bottleneck? I never found answers to these questions.

This big, global engine that needs to be set up also makes it a pain to get started. It took me several days to get a basic dataflow graph (the kind that's at the top of this readme) working, and that's after struggling mightily, reading the paper dozens of times and hacking the source in ways I didn't understand. Needless to say, I did not feel confident building upon such foundations.

reactive-web
------------
[reactive-web](https://github.com/nafg/reactive) was another inspiration. It is somewhat orthogonal to Scala.Rx, focusing more on eventstreams and integration with [Lift](http://liftweb.net/) while Scala.Rx focuses purely on time-varying values.

I did not like the fact that you had to program in a monadic style (i.e. living in `.map()` and `.flatMap()` and `for{}` comprehensions all the time) in order to take advantage of the change propagation. I found doing this extremely limiting, particularly in the case of nested `Rx`s such as that [given above](#nesting).

Knockout.js
-----------
[Knockout.js](http://knockoutjs.com/) does something similar for javascript, along with some other extra goodies like DOM-binding. In fact, the design and implementation and developer experience of the automatic-dependency-tracking is virtually identical (except for the greater verbosity of javascript)

Others
------
This idea of change propagation is also known as [Functional Reactive Programming](http://en.wikipedia.org/wiki/Functional_reactive_programming), and is a well studied field with a lot of research already done. Scala.Rx builds upon this research, and incorporates ideas from the following projects:

- [FlapJax](http://www.flapjax-lang.org/)
- [Frappe](http://www.imamu.edu.sa/dcontent/IT_Topics/java/10.1.1.80.4772.pdf)
- [Fran](http://conal.net/papers/icfp97/icfp97.pdf)

All of these projects are filled with good ideas. However, generally they are still on the researchy side: they require you to write your entire program in an obscure variant of an obscure language, with no hope at all for interop with existing, non-FRP code.

Design Considerations
=====================

Simple to Use
-------------
This meant that the syntax to write programs in a dependency-tracking way had to be as light weight as possible, and the programs had to *look* like their normal, old-fashioned, imperative counterparts. This meant using `DynamicVariable` instead of implicits to automatically pass arguments, sacrificing proper lexical scoping for nice syntax.

I ruled out using a purely monadic style (like [reactive-web](https://github.com/nafg/reactive)), as although it would be far easier to implement the library in that way, it would be a far greater pain to actually use it. Although I am happy to use for-comprehensions as loops and in specialized queries (e.g. [ScalaQuery](http://scalaquery.org/)) I'm not quite prepared to write my entire program in for-comprehensions, and still like the old-fashioned imperative style. I didn't want to have to manually declare dependencies. I wanted to be able to just write code, sprinkle a few `R{}`s around and have the dependency tracking and change propagation *just work*. Overall, I believe it has been quite successful at that!

Simple to Reason About
----------------------
This meant many things, but most of all it meant having no globals. This greatly simplifies many things for someone using the library, as you no longer need to reason about different parts of your program interacting through the library. Using Scala.Rx in different parts of a large program is completely fine; they are completely independent.

Another design decision in this area was to have the parallelism and propagation-scheduling be left mainly to an implicit `ExecutionContext`, and have the default to simply run the propagation wave on whatever thread made an update to the dataflow graph.

- The former means that anyone who is used to writing parallel programs in Scala/Akka is already familiar with how to deal with parallelizing Scala.Rx
- The lattermakes it far easier to reason about when propagations happen, at least in the default case: it simply happens *right away*, and by the time that `Var.update()` function has returned, the propagation has completed.

Overall, limiting the range of side effects and removing global state makes Scala.Rx easy to reason about, and means a developer can focus on using Scala.Rx to construct dataflow graphs rather than worry about un-predictable far-reaching interactions or performance bottlenecks.

Simple to Interop
-----------------
This meant that it had to be easy for a programmer to drop in and out of the FRP world. Many of the papers I covered in preparing for Scala.Rx described systems that worked brilliantly when on their own, and had some amazing properties, but required that the entire program be written in an obscure variant of an obscure language. No thought at all was given to interop.

With Scala.Rx, I resolved to do things differently. Hence, Scala.Rx:

- Is written in Scala: an uncommon, but probably less-obscure language than Haskell or Scheme
- Is a library: it is plain-old-scala. There is no source-to-source transformation, no special runtime, nothing. You download the source code into your Scala project, and start using it
- Allows you to use any programming language construct or library functionality within your `Rx`s: Scala.Rx will figure out the dependencies without the programmer having to worry about it
- Allows you to use Scala.Rx within a larger project without much pain. You can easily embed dataflow graphs within a larger object-oriented universe and interact with them via setting `Var`s and listening to `Obs`s.

Many of the papers I reviewed show a beautiful new FRP universe that we could be programming in, if only you ported all your code to FRP-Haskell and limited yourself to the small set of combinators used to create dataflow graphs. On the other hand, by letting you embed FRP snippets anywhere within existing code, using FRP ideas in existing projects without full commitment, and allowing you easy interop between your FRP and non-FRP code, Scala.Rx aims to bring the benefits FRP into the dirty, messy universe which we are programming in today.

Credits
=======

Copyright (c) 2013, Li Haoyi (haoyi.sg at gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.