Scala.Rx
========

Scala.Rx is an experimental change propagation library for Scala. Scala.Rx gives you time varying signals (`Sig`s), which are like smart variables who auto-update themselves when the values they depend on change. The underlying implementation is push-based FRP based on the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), and makes use of Scala's Delimited Continuations plugin.

A simple example which demonstrates the behavior is:

```scala
import rx._
val a = Var(1); val b = Var(2)
val c = Sig{ a() + b() }
println(c.now()) // 3
a() = 4
println(c.now()) // 6
```

The idea being that 99% of the time, when you re-calculate a variable, you re-calculate it the same way you initially calculated it. Furthermore, you only re-calculate it when one of the values it depends on changes. Scala.Rx does this for you automatically, and handling all the tedious update logic for you so you can focus on other things.

Furthermore, you no longer need to worry about forgetting to re-calculate some value when things change, and your different variables falling out of sync. Scala.Rx does this all for you.

Basic Use
=========

The above example is an executable Scala.Rx example; just make sure the rx jar is on your classpath and you compile with the Delimited Continuations plugin enabled.

We can compare and contrast the above example with the same program written the "classic" way:

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
val c = Sig{ a() + b() }
println(c.now()) // 3
```

The `Sig` function wraps the resultant `Int` in a `Sig[Int]`. The parenthesis `()` after `a` and `b` mean you want to extract the `Int` out of the `Var[Int]` and add `c` to the list of things notified everytime `a` or `b` change, and should only be used within a `Sig{}` block. On the other hand, the `now()` means you want to extract the `Int` out of the `Var[Int]` but don't want to notify anybody, and can be used anywhere.

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
val c = Sig{ someVeryExpensiveOperation(a(), b()) }

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
val c = Sig{ a() + b() }
val o = Obs(c){
  println("Value of C changed!")
}
a() = 4 // Value of C changed!
```

and attach listeners to the `Sig`, which fire when the `Sig`'s value changes. This is something you cannot do with memoized functions, short of repeatedly polling and checking over and over whether the value of `c` has changed. The ability to listen for changes makes it easy, for example, to allow changes to propagate over the network in order to keep some remote application in sync.

Apart from using the value of `Var`s in a `Sig`, `Sig`s themselves can be used in other `Sig`s

```scala
val a = Var(1) // 1

val b = Var(2) // 2

val c = Sig{ a() + b() } // 3
val d = Sig{ c() * 5 } // 15
val e = Sig{ c() + 4 } // 7
val f = Sig{ d() + e() + 4 } // 26

println(f.now()) // 26
a() = 3
println(f.now()) // 38
```

And they behave as you'd expect. As can be seen above, changing the value of `a` causes the change to propagate all the way through `c` `d` `e` to `f`.

Uninitialized Vars and Sigs
---------------------------

`Var`s can be initialized without a value, just with an initial type:

```scala
val a = Var[Int]; val b = Var[Int]
val c = Sig{ a() + b() }
```

When this is done, `a` and `b` are considered *uninitialized*. This means that when `c` tries to execute

```scala
{ a() + b() }
```

It first finds that `a` has not been initialized, and *pauses the evaluation of the expression* using Scala's Delimited Continuations plugin. If I then set `a`, via

```scala
a() = 1
```

It will evaluate `a()` and then pause at `b()`, which does not have a value. Only when I assign a value to `b` does the evaluation complete.

This means that if I instead defined `c` as:

```scala
val c = Sig{
    println(1)
    val a1 = a()
    println(2)
    val b1 = b()
    println(3)
    a1 + b1
} // prints 1
```

Only the first `println()` would fire initially. As I assigned values to `a` and `b`, I would be able to see the evaluation of the body of `c` proceed step by step:

```scala
a() = 5 // prints 2

b() = 5 // prints 3
```

And if you then re-assign `a` or `b`, the value of `c` will re-calculate from the beginning, as before.

You may realize that this behaves almost identically as the [Akka Dataflow DSL](http://doc.akka.io/docs/akka/snapshot/scala/dataflow.html), and it is indeed modelled after that. For that matter, "uninitialized `Var`s" are similar enough to `Future`s that a `Future` can be used in a `Sig{}` expression just fine:

```scala
val a = Var[Int]
val p = Promise[Int]

// c defined as before

a() = 5 // prints 2
p.success(5) // prints 3
```

Like `Var`s, `Sig`s themselves can be uninitialized if they depend on an unfulfilled `Future`, uninitialized `Var`, or another uninitialized `Sig`, and they behave as you would expect.

By-name parameters and Closures
-------------------------------

Scala.Rx is implemented using delimited continuations to pause the evaluation of the body of `Sig`s, the `a()` syntax use extract the value from a `Var` or `Sig` cannot be used directly in a by-name parameter or closure:

```scala
val a = Var(2);

val c = Sig{ Some(1).getOrElse(a()) } // doesn't compile

val d = Sig{ Seq(1, 2, 3).foreach(x => println(x * a()))} // doesn't compile
```

however, re-assigning them to temporary values outside the by-name-parameter/closure and using *that* inside the closure works perfectly:

```scala
val a = Var(2);

val c = Sig{ val a1 = a(); Some(1).getOrElse(a1) } // compiles

val d = Sig{ val a1 = a(); Seq(1, 2, 3).foreach(x => println(x * a1))} // compiles
```

The exact reason for this is subtle and mind-bending. In short, the Delimited Continuations plugin performs a lot of deep program transformations that do not play well with by-name-parameters or closures.
