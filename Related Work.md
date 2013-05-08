Related Work
============
Scala.Rx was not created in a vacuum, and borrows ideas and inspiration from a range of existing projects.

Scala.React
-----------
Scala.React, as described in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), contains a reactive change propagation portion (there called `Signal`s) which is similar to what Scala.Rx does. However, it does much more than that: It contains implementations for using event-streams, and multiple DSLs using delimited continuations in order to make it easy to write asynchronous workflows.

I have used this library, and my experience is that it is extremely difficult to set up and get started. It requires a fair amount of global configuration, with a global engine doing the scheduling and propagation, even running its own thread pools. This made it extremely difficult to reason about interactions between parts of the programs: would completely-separate dataflow graphs be able to affect each other through this global engine? Would the performance of multithreaded code start to slow down as the number of threads rises, as the engine becomes a bottleneck? I never found answers to many of these questions, and had did not manage to contact the author.

The global propagation engine also makes it difficult to get started. It took several days to get a basic dataflow graph (similar to the example at the top of this document) working. That is after a great deal of struggling, reading the relevant papers dozens of times and hacking the source in ways I didn't understand. Needless to say, these were not foundations that I would feel confident building upon.

reactive-web
------------
[reactive-web](https://github.com/nafg/reactive) was another inspiration. It is somewhat orthogonal to Scala.Rx, focusing more on event streams and integration with the [Lift](http://liftweb.net/) web framework, while Scala.Rx focuses purely on time-varying values.

Nevertheless, reactive-web comes with its own time-varying values (called Signals), which are manipulated using combinators similar to those in Scala.Rx (`map`, `filter`, `flatMap`, etc.). However, reactive-web does not provide an easy way to compose these Signals: the programmer has to rely entirely on `map` and `flatMap`, possibly using Scala's for-comprehensions.

I did not like the fact that you had to program in a monadic style (i.e. living in `.map()` and `.flatMap()` and `for{}` comprehensions all the time) in order to take advantage of the change propagation. This is particularly cumbersome in the case of [nested `Rx`s](Basic-Usage#nesting), where Scala.Rx's

```scala
// a b and c are Rxs
x = Rx{ a() + b().c() }
```

becomes

```scala
x = for {
  va <- a
  vb <- b
  vc <- vb.c
} yield (va + vc)
```

As you can see, using for-comprehensions as in reactive-web results in the code being significantly longer and much more obfuscated. 

Knockout.js
-----------
[Knockout.js](http://knockoutjs.com/) does something similar for Javascript, along with some other extra goodies like DOM-binding. In fact, the design and implementation and developer experience of the automatic-dependency-tracking is virtually identical. This:

```javascript
this.firstName = ko.observable('Bob');
this.lastName = ko.observable('Smith');
fullName = ko.computed(function() {
    return this.firstName() + " " + this.lastName();
}, this);
```

is semantically equivalent to the following Scala.Rx code:

val firstName = Var("Bob")
val lastName = Var("Smith")
fullName = Rx{ firstName() + " " + lastName() }

a `ko.observable` maps directly onto a `Var`, and a `kocomputed` maps directly onto an `Rx`. Apart from the longer variable names and the added verbosity of Javascript, the semantics are almost identical.

Apart from providing an equivalent of `Var` and `Rx`, Knockout.js focuses its efforts in a different direction. It lacks the majority of the useful combinators that Scala.Rx provides, but provides a great deal of other functionality, for example integration with the browser's DOM, that Scala.Rx lacks.

Others
------
This idea of change propagation, with time-varying values which notify any value which depends on them when something changes, part of the field of [Functional Reactive Programming](http://en.wikipedia.org/wiki/Functional_reactive_programming). This is a well studied field with a lot of research already done. Scala.Rx builds upon this research, and incorporates ideas from the following projects:

- [FlapJax](http://www.flapjax-lang.org/)
- [Frappe](http://www.imamu.edu.sa/dcontent/IT_Topics/java/10.1.1.80.4772.pdf)
- [Fran](http://conal.net/papers/icfp97/icfp97.pdf)

All of these projects are filled with good ideas. However, generally they are generally very much research projects: in exchange for the benefits of FRP, they require you to write your entire program in an obscure variant of an obscure language, with little hope inter-operating with existing, non-FRP code. 

Writing production software in an unfamiliar paradigm such as FRP is already a significant risk. On top of that, writing production software in an unfamiliar language is an additional variable, and writing production software in an unfamiliar paradigm in an unfamiliar language *with no inter-operability with existing code* is downright reckless. Hence it is not surprising that these libraries have not seen significant usage. Scala.Rx aims to solve these problems by providing the benefits of FRP in a familiar language, with seamless interop between FRP and more traditional imperative or object-oriented code.
