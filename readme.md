Scala.Rx
========

Scala.Rx is an experimental change propagation library for Scala. Scala.Rx gives you Reactive variables (`Rx`s), which are smart variables who auto-update themselves when the values they depend on change. The underlying implementation is push-based FRP based on the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf).

A simple example which demonstrates the behavior is:

```scala
import rx._
val a = Var(1); val b = Var(2)
val c = Rx{ a() + b() }
println(c.now()) // 3
a() = 4
println(c.now()) // 6
```

The idea being that 99% of the time, when you re-calculate a variable, you re-calculate it the same way you initially calculated it. Furthermore, you only re-calculate it when one of the values it depends on changes. Scala.Rx does this for you automatically, and handling all the tedious update logic for you so you can focus on other things.

Furthermore, you no longer need to worry about forgetting to re-calculate some value when things change, and your different variables falling out of sync. Scala.Rx does this all for you.

Basic Use
=========

The above example is an executable program. In general, `import rx._` is enough to get you started with Scala.Rx, and it will be assumed in all further examples. These examples are all taken from the unit tests.

The basic entities you have to care about are `Var`, `Rx` and `Obs`:

- a `Var` is a smart variable which you can get using `a()` and set using `a() = ...`. Whenever its value changes, it notifies any downstream entity which needs to be recalculated.
- a `Rx` is a reactive definition which automatically captures any `Var`s or other `Rx`s which get called in its body, flagging them as dependencies and re-calculating whenever one of them changes. Like a `Var`, you can use the `a()` syntax to retrieve its value, and it also notifies downstream entities when the value changes.
- a `Obs` is an observer which looks at a single `Var` or `Rx`, performing some side-effecting function when it changes value.

Apart from using the value of a `Var` in a `Rx`, as shown above, the `Rx` itself can be used in another `Rx`:

```scala
val a = Var(1) // 1

val b = Var(2) // 2

val c = Rx{ a() + b() } // 3
val d = Rx{ c() * 5 } // 15
val e = Rx{ c() + 4 } // 7
val f = Rx{ d() + e() + 4 } // 26

println(f.now()) // 26
a() = 3
println(f.now()) // 38
```

And they behave as you'd expect. As can be seen above, changing the value of `a` causes the change to propagate all the way through `c` `d` `e` to `f`. You can use a `Var` and `Rx` anywhere you an use a normal variable.

The changes propagate through the dependency graph in *cycles*. Each update to a `Var` touches off a propagation cycle, which pushes the changes from that `Var` to any `Rx` which is (directly or indirectly) dependent on its value. In the process, it is possible for a `Rx` to be re-calculated more than once.

`Obs`s can be used to observe `Rx`s and `Var`s and perform side effects when they change:

```scala
val a = Var(1)
var count = 0
val o = Obs(a){
    count = count + 1
}
a() = 2
assert(count === 1)
```

The body of `Rx`s should be side effect free, as they may be run more than once per propagation cycle. You should use `Obs`s to perform your side effects, as they ar guaranteed to run only once per propagation cycle when the values for all `Rx`s have stabilized.

Error Handling
--------------

Since the body of an `Rx` can be any arbitrary scala code, it can throw exceptions. Rather than propagating up the call stack, any exceptions are caught by the `Rx` itself and stored internally as a `Try`.

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

In this example, initially all the values for `a`, `b`, `c`, `d`, `e`, `f` and `g` are well defined. However, when `b` is set to `0`, `c` and `e` both result in exceptions, and the exception from `c` propagates to `g`. Attempting to extract the value from, for example, `g` using `g()` will re-throw the ArithmeticException.

Nesting
-------

`Rx`s can contain other `Rx`s, arbitrarily deeply. This example shows the `Rx`s nested two deep:

```scala
val a = Var(1)
val b = Rx{
    Rx{ a() } -> Rx{ math.random }
}
val r = b()._2()
a() = 2
assert(b()._2() === r)
```

In general, nested `Rx`s behave as you would expect. A slightly less contrived example may be:

```scala
trait WebPage{
    val time = Var(System.currentTimeMillis())
    def update(): Unit
}
class HomePage{
    val html = Rx{"Home Page! time: " + time()}
}
class AboutPage{
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
```

In this case, we define a web page which has a `html` value (a `Rx[String]`). However, depending on the `url`, it could be either a `HomePage` or an `AboutPage`, and so our `page` object is a `Rx[WebPage]`.

Having a `Rx[WebPage]`, where the `WebPage` has an `Rx[String]` inside, seems natural and obvious, and Scala.Rx lets you do it simply and naturally.

Basic Combinators
-----------------

Scala.Rx also provides a set of combinators which allow your to easily transform your `Rx`s.

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

`map` does what you would expect, creating a new `Rx` with the value of the old `Rx` transformed by some function.

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

`filter` ignores changes to the value of the `Rx` that fail the predicate. It optionally takes a predicate for the `Failure` case, ignoring changes if the value transitions from one `Failure` to another.

Closely related to it are:

- `filterDiff`: Giving you both the old and the new value to use when deciding whether or not to accept a change.
- `filterTry`: Similar to `filterDiff`, except it gives you the old and new values as `Try`s to work with.
- `skipFailures`: A shorthand for a filter which ignores changes which are `Failure`s. If the original `Rx` transitions from `Success` to `Failure`, the `.skipFailure` version will simply remain at the last `Success` state.

Advanced Combinators
--------------------

These are combinators which do more than simply transforming a value from one to another.

###Debounce

```scala
val a = Var(10)
val b = a.debounce(50 millis)
val c = Rx( a() * 2 ).debounce(50 millis)
var count = 0
val ob = Obs(b){ count += 1 }
val oa = Obs(c){ count += 1 }

a() = 5
assert(b() === 5)
assert(c() === 10)

a() = 2
assert(b() === 5)
assert(c() === 10)

a() = 4
assert(b() === 5)
assert(c() === 10)

a() = 7
assert(b() === 5)
assert(c() === 10)

eventually{
    assert(b() === 7)
    assert(c() === 14)
}
```

`debounce` creates a new `Rx` which does not change more than once every `interval` units of time. No matter how many times the original `Rx` changes, the `debounced` version will only update once every interval, and the last un-applied change will be stored and applied at the end of the interval if need be.

In this example, you can see that after initially setting `a() = 5`, with `b() === 5, c() === 10`, subsequent changes to a() have no effect on `b` or `c` until the `eventually{}` block at the bottom. At that point, the interval will have passed, and `b` and `c` will update to use the most recent value of `a`.

`debounce` optionally takes a second parameter `delay`, which is an initial lag before any updates happen.

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
eventually{99)
}
```

The value of `b()` updates as you would expect as the series of `Future`s complete (in this case, manually using `Promise`s).

This is handy if your dependency graph contains some asynchronous elements. For example, you could have a `Rx` which depends on another `Rx`, but requires an asynchronous web request to calculate its final value. With `async`, the results from the asynchronous web request will be pushed back into the change propagation graph automatically when the `Future` completes, starting off another propagation cycle and conveniently updating the rest of the graph which depends on the new result.

`async` optionally takes a second argument which causes out-of-order `Future`s to be dropped. This is useful if you always want to have the result of the most recently-created `Future` which completed.

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

A `Timer` is a `Rx` that generates events on a regular basis. The events are based on the `scheduler` of the implicit `ActorSystem`, which defaults to a maximum precision of about 100 milliseconds. In the example above, the `for`-loop checks that the value of the timer `t()` increases over time from 0 to 5, and then checks that `count` has been incremented at least that many times

It automatically cancels the scheduled task when the `Timer` object becomes unreachable, so it can be garbage collected, so you don't need to worry about managing the life-cycle of the `Timer`.

Why Scala.Rx
============

We can compare and contrast the first example:

```scala
import rx._
val a = Var(1); val b = Var(2)
val c = Rx{ a() + b() }
println(c.now()) // 3
a() = 4
println(c.now()) // 6
```
 
with the same program written the "classic" way:

```scala
var a = 1; var b = 2
var c = a + b
println(c) // 3
a = 4
println(c) // 3
```

Scala.Rx programs look almost like normal programs, except for a few things. This:

```scala
var a = 1; var b = 2
```

becomes

```scala
val a = Var(1); val b = Var(2)
```

Where a `Var` wraps the `Int` value `1` in a smart variable (of type `Var[Int]`), which can notify its dependents whenever its value changes.

For calculated values, instead of

```scala
var c = a + b
println(c)
```

you write

```scala
val c = Rx{ a() + b() }
println(c.now()) // 3
```

The `Rx` function wraps the resultant `Int` in a `Rx[Int]`. The parenthesis `()` after `a` and `b` calls the `.apply()` method, extracting the `Int` out of the `Var[Int]` and adding `c` to the list of things notified everytime `a` or `b` change. If you want the value but do not want to define a dependency, `.now()` lets you extract the `Int` without doing so. The two functions are equivalent when used outside of a `Rx{}` block.

This is somewhat more verbose, but it means that you no longer need to remember to update `c` when `a` or `b` change; Scala.Rx will do it for you automatically. Hence, in:

```scala
a = 4
println(c) // 3
```

`c` still has the old value, even though the value of `a` has been updated! But with Scala.Rx:

```scala
a() = 4
println(c.now()) // 6
```

You can see the value of `c` has been automatically updated to reflect the new value.

Now, you may ask, how is this different from:

```scala
var a = 1; var b = 2
def c = a + b
println(c) // 3
a = 4
println(c) // 6
```

Which also ensures you always have the most up-to-date value for `c`. The difference is that in this case, `c` is re-calculated *every single time* you call it, while using Scala.Rx, `c` is only re-calculated only when the values it depends on change. Although in this example, the `+` operation is cheap, consider a slightly different example:

```scala
var a = 1; var b = 2;
def c = someVeryExpensiveOperation(a, b)

println(c) // 3, someVeryExpensiveOperation is executed
println(c) // 3, someVeryExpensiveOperation is executed
a = 4
println(c) // 6, someVeryExpensiveOperation is executed
println(c) // 6, someVeryExpensiveOperation is executed
```

`someVeryExpensiveOperation` is now executed every time the value of `c` is requested! If `a` and `b` don't change much but `c` is requested a lot, this is clearly inefficient. However, with Scala.Rx

```scala
val a = Var(1); val b = Var(2)
val c = Rx{ someVeryExpensiveOperation(a(), b()) }

println(c.now()) // 3
println(c.now()) // 3
a() = 4            // someVeryExpensiveOperation is executed
println(c.now()) // 6
println(c.now()) // 6
```

`someVeryExpensiveOperation` is only executed once, when the value of `a` changes.

Next, you may ask, how is this different from making `c` a function, and memoizing it? In fact, they are very similar, in that both Scala.Rx and memoized-functions recalculate the values only when necessary. However, due to the fact that Scala.Rx is push-based, you can do things like this:

```scala
val a = Var(1); val b = Var(2)
val c = Rx{ a() + b() }
val o = Obs(c){
  println("Value of C changed!")
}
a() = 4 // Value of C changed!
```

and attach listeners to the `Rx`, which fire when the `Rx`'s value changes. This is something you cannot do with memoized functions, short of repeatedly polling and checking over and over whether the value of `c` has changed. The ability to listen for changes makes it easy, for example, to allow changes to propagate over the network in order to keep some remote application in sync.


How it Works
============


Dependency Tracking
-------------------
Scala.Rx tracks the dependency graph between different `Var`s and `Rx`s without any explicit annoation by the programmer. This means that in (almost) all cases, you can just write your code as if it wasn't being tracked, and Scala.Rx would build up the dependency graph automatically.

Every time the body of an `Rx{...}` is evaluated (or re-evaluated), it is put into a `DynamicVariable`. Any calls to the `.apply()` methods of other `Rx`s then inspect this stack to determine who (if any) is `Rx` who called, creating a dependency between them. Thus a dependency graph is implicitly created without any action on the part of the programmer.

The dependency-tracking strategy of Scala.Rx is based of a subset of the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), in particular their definition of "Opaque Signals". The implementation follows it reasonably closely.

Propagation
-----------

###Forward References
Once we have evaluated our `Var`s and `Rx`s once and have a dependency graph, how do we keep track of our children (the `Rx`s who depend on us) and tell them to update? Simply keeping a `List[]` of all children will cause memory leaks, as the `List[]` will prevent any child from being garbage collected even if all other references to the child have been lost and the child is otherwise unaccessable.

Instead, Scala.Rx using a list of `WeakReference`s. These allow the `Rx` to keep track of its children while still letting them get garbage collected when all other references to them are lost. When a child becomes unreachable and gets garbage collected, the `WeakReference` becomes `null`, and these null references get cleared from the list every time it is updated.

###Propagation Strategies
The default propagation of changes is done in a breadth-first, topologically-sorted order, similar to that described in the paper. Each propagation cycle occurs when a `Var` is set, e.g. in
 
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

Hence, it is possible that an `Rx` will get evaluated more than once, even if only a single `Var` is updated. You should ensure that the body of any `Rx`s can tolerate being run more than once without harm. If you need to perform side effects, use an `Obs`, which only executes its side effects once per propagation cycle after the values for all `Rx`s have stabilized.

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

Where `propagate` method takes a `Seq` of updates that must happen: every propagation cycle, there is a set of `Emitter`s telling `Reactor`s to update. Now you can have the propagation happen in any order you want

Concurrency and Asynchrony
--------------------------

As mentioned earlier, by default everything happens on a single-threaded execution context and there is no parallelism. By using a custom ExecutionContext, it is possible to have the updates in each propagation cycle happen in parallel, but there still aren't any race conditions because only updates which are completely independent will occur in parallel, and there is no risk of a single `Rx` being asked to update more than once in parallel.

This is not the whole picture, though. The asynchronous combinators may spontaneously trigger propagation cycles when their async operations complete. For example, the `Timer` signal will fire off events every time the interval passes, or the `Async` combinator's `Future[T]` may complete, causing it to update and begin a propagation cycle. Or, you may have multiple people calling `x() = ...` in parallel from multiple threads. These are all valid uses.

###Agents 

In the case where multiple propagation cycles are happening simultaneously, concurrency and parallelism is managed via [Akka Agents](http://doc.akka.io/docs/akka/2.1.0/scala/agents.html). These are, effectively, mini-[Actors](http://doc.akka.io/docs/akka/2.1.0/scala/actors.html) which force updates to a single `Rx` to happen sequentially. If more than one propagation cycle tells it to update, the updates are queued up and occur one at a time. Hence the body of each individual `Rx{...}` is will not be run multiple times in parallel, even if the body of different `Rx{..}`s may be run in concurrently. Assuming the body of the `Rx{...}` is "pure" and has minimal side effects, this should not cause problems.

###Weak-References

What about the weak references? These are less convenient, as unlike the rest of the state related to each `Rx`, the weak references pointing toward an `Rx` are not kept within the `Rx` itself, but instead kept in its parents. Hence updates to these weak references cannot conveniently be seralized by encapsulating the state within an Agent.

Instead, Scala.Rx does two things:

- Make the list of `WeakReferences` append-only
- Maintains a list of Parents in each Child, in addition to having a list of Children in each Parent. This list of parents will then be kept up to date, and updates to it will be serialized when the `Rx`'s Agent updates.

As a result, although the _forward_ references from parent to child may not always be kept up to date, they will always form a super-set of the "correct" relationships. These "correct" relationships will be kept up to date in the _backward_ references from child to parent, and will ensure that things behave correctly even if the set of forward references is larger than it needs to be.

Related Work
============

Scala.React
-----------
Scala.React, as described in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), contains the reactive change propagation portion (there called `Signal`s) which is similar to what Scala.Rx does. However, it does much more than that with its event-streams and multiple DSLs using delimited continuations to do fancy things.

However, I found it a pain to set up, requiring a bunch of global configuration, having its own custom-executor, even running its own thread pools. Overall, I thought it required far too much effort to get even partially working, and introduced far too much complexity for what it does.

reactive-web
------------
[reactive-web](https://github.com/nafg/reactive) was another inspiration. It is somewhat orthogonal to Scala.Rx, focusing more on eventstreams and integration with [Lift](http://liftweb.net/) while Scala.Rx focuses purely on time-varying values.

I did not like the fact that you had to program in a monadic style (i.e. living in `.map()` and `.flatMap()` and `for{}` comprehensions all the time) in order to take advantage of the change propagation.

Knockout.js
-----------
[Knockout.js](http://knockoutjs.com/) does something similar for javascript, along with some other extra goodies like DOM-binding. In fact, the design and implementation and developer experience of the automatic-dependency-tracking is virtually identical (except for the greater verbosity of javascript)

Others
------
This idea of change propagation is also known as [Functional Reactive Programming](http://en.wikipedia.org/wiki/Functional_reactive_programming), and is a well studied field with a lot of research already done. Scala.Rx builds upon this research, and incorporates ideas from the following projects:

- [FlapJax](http://www.flapjax-lang.org/)
- [Frappe](http://www.imamu.edu.sa/dcontent/IT_Topics/java/10.1.1.80.4772.pdf)
- [Fran](http://conal.net/papers/icfp97/icfp97.pdf)

Design Considerations
=====================

Simple to Use
-------------
This meant that the syntax to write programs in a dependency-tracking way had to be as light weight as possible, and the programs had to *look* like their normal, old-fashioned, imperative counterparts. This meant using `DynamicVariable` instead of implicits to automatically pass arguments, sacrificing proper lexical scoping for nice syntax.

I ruled out using a purely monadic style (like [reactive-web](https://github.com/nafg/reactive)), as although it would be far easier to implement the library in that way, it would be a far greater pain to actually use it. Although I am happy to use for-comprehensions as loops and in specialized queries (e.g. [ScalaQuery](http://scalaquery.org/)) I'm not quite prepared to write my entire program in for-comprehensions, and still like the old-fashioned imperative style.

No Globals
----------
This greatly simplifies many things for someone using the library, as you no longer need to reason about different parts of your program interacting through the library. Using Scala.Rx in different parts of a large program is completely fine; they are completely independent.

However, it also means that there can be no special-threads, no global contention manager, no global propagation scheduler. These are the things which I found most confusing trying to understand the workings of Scala.React, and took the longest time in setting up properly to work. Even though it makes implementing the library somewhat trickier to work without globals, I think they are a worthy omission.

Build on Standards
------------------
This means using [scala.concurrent.Future](http://docs.scala-lang.org/sips/pending/futures-promises.html) and [Akka](http://akka.io/) as much as possible. Not only does it mean that I don't need to spend effort implementing my own (probably buggy and inferior) algorithms and techniques, it means that any users who have experience with these existing systems will already be familiar with their characteristics.

For example, to make Scala.Rx to run in a single thread, you simply need to define the the right [ExecutionContext](http://www.scala-lang.org/archives/downloads/distrib/files/nightly/docs/library/index.html#scala.concurrent.ExecutionContext), which a user is more likely to be familiar with (since its what you would use to make *any* `Future` using program run in a single thread) than with some special home-brewed system.

No Delimited Continuations
--------------------------
Using the delimited continuations plugin would in theory have solved many problems. For example, using it, we would be able to pause the execution of any `Rx` at any time, which would mean we could completely avoid redundantly-recomputing the body of a `Rx`. It also should bring many other benefits, such as seamless integration with `Future`s and the [Akka Dataflow](http://doc.akka.io/docs/akka/snapshot/scala/dataflow.html).

However, the continuations plugin proved to be far too rough around the edges, when I actually implemented Scala.Rx using it. It plays badly (e.g. does not work at all) with higher-order functions and by-name parameters, which form a huge portion of the standard library. It also caused bugs with implicit-resolutions and run-time ClassCastExceptions. In general, it added far more pain than it relieved.

Credits
-------

Copyright (c) 2013, Li Haoyi (haoyi.sg at gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.