Scala.Rx
========

Scala.Rx is an experimental change propagation library for Scala. Scala.Rx gives you time varying Rxnals (`Rx`s), which are like smart variables who auto-update themselves when the values they depend on change. The underlying implementation is push-based FRP based on the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf).

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

The basic entities you have to care about are `Var`s, `Rx`s and `Obs`s:

- a `Var` is a smart variable which you can get using `a()` and set using `a() = ...`. Whenever its value changes, it notifies any downstream entity which needs to be recalculated.
- a `Rx` is a reactive definition which automatically captures any `Var`s or other `Rx`s which get called in its body, flagging them as dependencies and re-calculating whenever one of them changes.
- a `Obs` is an observer which looks at a single `Var` or `Rx`, performing some side-effecting function when it changes value

Apart from using the value of `Var`s in a `Rx`, as shown above, `Rx`s themselves can be used in other `Rx`s

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

And they behave as you'd expect. As can be seen above, changing the value of `a` causes the change to propagate all the way through `c` `d` `e` to `f`. You can use `Var`s and `Rx`s anywhere you an use a normal variable.

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

However, when the value of `a` becomes `0`, the body of `b` throws an `ArithmeticException`. This is caught by `b` and only released if you try to extract the value from `b` using `b()`. You can extract the entire `Try` using `toTry` and pattern match on it to handle both the `Success` case as well as the `Failure` case.

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

val a = Var(1)
val b = Rx{
    Rx{ a() } -> Rx{ math.random }
}
val r = b()._2()
a() = 2
assert(b()._2() === r)

In general, nested `Rx`s behave as you would expect.

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

Optionally takes a second parameter `delay`, which is an initial lag before any updates happen.

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

`async` can `Future`s as many times as necessary:

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

Making it handy if your dependency graph contains some asynchronous elements. For example, you could have a `Rx` which depends on another `Rx`, but requires an asynchronous web request to calculate. With `async`, the results from the asynchronous web request will be pushed back into the change propagation graph automatically when the `Future` completes, conveniently updating the rest of the graph with the result without the programmer having to do a thing.

`async` optionally takes a second argument which causes out-of-order `Future`s to be dropped. This is useful if you always want to have the result of the most recently-created `Future` which completed.

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

The `Rx` function wraps the resultant `Int` in a `Rx[Int]`. The parenthesis `()` after `a` and `b` mean you want to extract the `Int` out of the `Var[Int]` and add `c` to the list of things notified everytime `a` or `b` change, and should only be used within a `Rx{}` block. On the other hand, the `now()` means you want to extract the `Int` out of the `Var[Int]` but don't want to notify anybody, and can be used anywhere.

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

Scala.Rx is based of a subset of the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), in particular their definition of "Opaque Signals". The implementation follows it reasonably closely: each time an `Rx` is evaluated (or re-evaluated), it is put into a `DynamicVariable`. Any calls to the `.apply()` methods of other `Rx`s then inspect this stack to determine who (if any) is `Rx` who called, creating a dependency between them. Thus a dependency graph is implicitly created without any action on the part of the programmer.

The actual propagation of changes is done in a breadth-first, topologically-sorted order, similar to that described in the paper. Nodes earlier in the dependency graph being evaluated before those down the line. However, due to the fact that the dependencies of a `Rx` are not known until it is evaluated, it is impossible to strictly maintain this invariant at all times, since the underlying graph could change unpredictably.

In general, Scala.Rx keeps track of the topological order dynamically, such that after initialization, if the dependency graph does not change to radically, most nodes *should* be evaluated only once per propagation, but this is not a hard guarantee. Furthermore, Scala.Rx makes extensive use of [ScalaSTM](http://nbronson.github.com/scala-stm/) to handle concurrency. This means that the body of `Rx`s could be executed by multiple threads in parallel (in different transactions), and re-executed if there is contention.

Hence, it is possible that an `Rx` will get evaluated more than once, even if only a single `Var` is updated. You should ensure that the body of any `Rx`s can tolerate being run more than once without harm. If you need to perform side effects, use an `Obs`, which only executes its side effects once per propagation cycle after the values for all `Rx`s have stabilized.

