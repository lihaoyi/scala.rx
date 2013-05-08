Basic Combinators
-----------------
Scala.Rx also provides a set of combinators which allow your to easily transform your `Rx`s; this allows the programmer to avoid constantly re-writing logic for the common ways of constructing the dataflow graph. The three basic combinators: `map()`, `filter()` and `reduce()` are modelled after the scala collections library, and provide an easy way of transforming the values coming out of a `Rx`.

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
These are combinators which do more than simply transforming a value from one to another. Many of them have asynchronous effects, and can spontaneously modify the dataflow graph and begin propagations without any external trigger. Although this may sound somewhat unsettling, the functionality provided by these combinators is often necessary, and manually writing the logic around something like Debouncing, for example, is far more error prone than simply using the combinatory.

###Async

```scala
val p = Promise[Int]()
val a = Rx{
  p.future
}.async(10)
assert(a() === 10)

p.success(5)
assert(a() === 5)
```

The `async` combinator only applies to `Rx[Future[_]]`s. It takes an initial value, which will be the value of the `Rx` until the `Future` completes, at which point the the value will become the value of the `Future`.

`async` can create `Future`s as many times as necessary. This example shows it creating two distinct `Future`s:

```scala
var p = Promise[Int]()
val a = Var(1)
val b = Rx{
  val A = a()
  p.future.map{_ + A}
}.async(10)
assert(b() === 10)

p.success(5)
assert(b() === 6)

p = Promise[Int]()
a() = 2
assert(b() === 6)

p.success(7)
assert(b() === 9)
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

The scheduled task is cancelled automatically when the `Timer` object becomes unreachable, so it can be garbage collected. This means you do not have to worry about managing the life-cycle of the `Timer`. On the other hand, this means the programmer should ensure that the reference to the `Timer` is held by the same object as that holding any `Rx` listening to it. This will ensure that the exact moment at which the `Timer` is garbage collected will not matter, since by then the object holding it (and any `Rx` it could possibly affect) are both unreachable. 

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

If multiple updates happen with a short span of time (less than `t` apart), the first update will take place immediately, and a second update will take place only after the time `t` has passed. For example, this may be used to limit the rate at which an expensive result is re-calculated: you may be willing to let the calculated value be a few seconds stale if it lets you save on performing the expensive calculation more than once every few seconds.

**Next Section: [How it Works](https://github.com/lihaoyi/scala.rx/wiki/How-it-Works)**
