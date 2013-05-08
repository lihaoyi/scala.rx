Design Considerations
=====================

Simple to Use
-------------
This meant that the syntax to write programs in a dependency-tracking way had to be as light weight as possible, that programs written using FRP had to *look* like their normal, old-fashioned, imperative counterparts. This meant using `DynamicVariable` instead of implicits to automatically pass arguments, sacrificing proper lexical scoping for nice syntax.

I ruled out using a purely monadic style (like [reactive-web](https://github.com/nafg/reactive)), as although it would be far easier to implement the library in that way, it would be a far greater pain to actually use it. I also didn't want to have to manually declare dependencies, as this violates [DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself) when you are declaring your dependencies twice: once in the header of the `Rx`, and once more when you use it in the body. 

The goal was to be able to write code, sprinkle a few `Rx{}`s around and have the dependency tracking and change propagation *just work*. Overall, I believe it has been quite successful at that!

Simple to Reason About
----------------------
This means many things, but most of all it means having no globals. This greatly simplifies many things for someone using the library, as you no longer need to reason about different parts of your program interacting through the library. Using Scala.Rx in different parts of a large program is completely fine; they are completely independent.

Another design decision in this area was to have the parallelism and propagation-scheduling be left mainly to an implicit `ExecutionContext`, and have the default to simply run the propagation wave on whatever thread made the update to the dataflow graph.

- The former means that anyone who is used to writing parallel programs in Scala/Akka is already familiar with how to deal with parallelizing Scala.Rx
- The latter makes it far easier to reason about when propagations happen, at least in the default case: it simply happens *right away*, and by the time that `Var.update()` function has returned, the propagation has completed.

Overall, limiting the range of side effects and removing global state makes Scala.Rx easy to reason about, and means a developer can focus on using Scala.Rx to construct dataflow graphs rather than worry about un-predictable far-reaching interactions or performance bottlenecks.

Simple to Interop
-----------------
This meant that it had to be easy for a programmer to drop in and out of the FRP world. Many of the papers I read in preparing for Scala.Rx described systems that worked brilliantly on their own, and had some amazing properties, but required that the entire program be written in an obscure variant of an obscure language. No thought at all was given to inter-operability with existing languages or paradigms, which makes it impossible to incrementally introduce FRP into an existing codebase.

With Scala.Rx, I resolved to do things differently. Hence, Scala.Rx:

- Is written in Scala: an uncommon, but probably less-obscure language than [Haskell](http://www.haskell.org/haskellwiki/Haskell) or [Scheme](http://racket-lang.org/)
- Is a library: it is plain-old-scala. There is no source-to-source transformation, no special runtime necessary to use Scala.Rx. You download the source code into your Scala project, and start using it
- Allows you to use any programming language construct or library functionality within your `Rx`s: Scala.Rx will figure out the dependencies without the programmer having to worry about it, without limiting yourself to some inconvenient subset of the language
- Allows you to use Scala.Rx within a larger project without much pain. You can easily embed dataflow graphs within a larger object-oriented universe and interact with them via setting `Var`s and listening to `Obs`s

Many of the papers I reviewed show a beautiful new FRP universe that we could be programming in, if only you ported all your code to FRP-Haskell and limited yourself to the small set of combinators used to create dataflow graphs. On the other hand, by letting you embed FRP snippets anywhere within existing code, using FRP ideas in existing projects without full commitment, and allowing you easy interop between your FRP and non-FRP code, Scala.Rx aims to bring the benefits FRP into the dirty, messy universe which we are programming in today.

Limitations
===========
Scala.Rx has a number of significant limitations, some of which arise from trade-offs in the design, others from the limitations of the underlying platform.

No "Empty" Reactives
--------------------
The API of Rxs in Scala.Rx tries to follow the collections API as far as possible: you can map, filter and reduce over the Rxs, just as you can over collections. However, it is currently impossible to have a Rx which is empty in the way a collection can be empty: filtering out all values in a Rx will still leave at least the initial value (even if it fails the predicate) and Async Rxs need to be given an initial value to start.

This limitation arises from the difficulty in joining together possibly empty Rxs with good user experience. For example, if I have a dataflow graph:

```scala
val a = Var()
val b = Var()
var c = Rx{
    .... a() ...
    ... some computation ...
    ... b() ...
    result
}
```

Where `a` and `b` are initially empty, I have basically two options:

- Block the current thread which is computing `c`, waiting for `a` and then `b` to become available.
- Throw an exception when `a()` and `b() are requested, aborting the computation of `c` but registering it to be restarted when `a()` or `b()` become available.
- Re-write this in a monadic style using for-comprehensions.
- Use the delimited continuations plugin to transform the above code to monadic code automatically.

The first option is a performance problem: threads are generally extremely heavy weight on most operation systems. You cannot reasonably make more than a few thousand threads, which is a tiny number compared to the amount of objects you can create. Hence, although blocking would be the easiest, it is frowned upon in many systems (e.g. in Akka, which Scala.Rx is built upon) and does not seem like a good solution.

The second option is a performance problem in a different way: with `n` different dependencies, all of which may start off empty, the computation of `c` may need to be started and aborted `n` times even before completing even once. Although this does not block any threads, it does seem extremely expensive.

The third option is a no-go from a user experience perspective: it would require far reaching changes in the code base and coding style in order to benefit from the change propagation, which I'm not willing to require. 

The last option is problematic due to the bugginess of the delimited continuations plugin. Although in theory it should be able to solve everything, a large number of small bugs (messing up type inferencing, interfering with implicit resolution) combined with a few fundamental problems meant that even on a small scale project (less than 1000 lines of reactive code) it was getting painful to use.\

No Automatic Parallelization at the Start
-----------------------------------------
As mentioned earlier, Scala.Rx can perform automatic parallelization of updates occuring in the dataflow graph: simply provide an appropiate ExecutionContext, and independent Rxs will have their updates spread out over multiple cores.

However, this only works for updates, and not when the dataflow graph is being initially defined: in that case, every Rx evaluates its body once in order to get its default value, and it all happens serially on the same thread. This limitation arises from the fact that we do not have a good way to work with "empty" Rxs, and we do not know what an Rxs dependencies are before the first time we evaluate it. 

Hence, we cannot start all our Rxs evaluating in parallel as some may finish before others they depend on, which would then be empty, their initial value still being computed. We also cannot choose to parallelize those which do not have dependencies on each other, as before execution we do not know what the dependencies are! 

Thus, we have no choice but to have the initial definitions of Rxs happen serially. If necessary, a programmer can manually create independent Rxs [in parallel using Futures](http://stackoverflow.com/questions/12923429/easy-parallel-evaluation-of-tuples-in-scala).

Glitchiness and Redundant Computation
-------------------------------------

In the context of FRP, a glitch is a temporary inconsistency in the dataflow graph. Due to the fact that updates do not happen instantaneously, but instead take time to computer, the values within an FRP system may be transiently out of sync during the update process. Furthermore, depending on the nature of the FRP system, it is possible to have nodes be updated more than once in a propagation.

This may or may not be a problem, depending on how tolerant the application is of occasional stale inconsistent data. In a single-threaded system, it can be avoided in a number of ways

- Make the dataflow graph static, and perform a topological sort to rank nodes in the order they are to be updated. This means that a node *always* is updated *after* its dependencies, meaning they will never see any stale data
- Pause the updating of a node when it tries to call upon a dependency which has not been updated. This could be done by blocking the thread, for example, and only resuming after the dependency has been updated.

However, both of these approaches have problems. The first approach is extremely constrictive: a static dataflow graph means that a large amount of useful behavior, e.g. creating and destroying sections of the graph dynamically at run-time, is prohibited. This goes against Scala.Rx's goal of allowing the programmer to write code "normally" without limits, and letting the FRP system figure it out.

The second case is a problem for languages which do not easily allow computations to be paused. In Java, and by extension Scala, the threads used are operating system (OS) threads which are extremely expensive. Hence, blocking an OS thread is frowned upon. Coroutines and continuations could also be used for this, but Scala lacks both of these facilities.

The last problem is that both these models only make sense in the case of single threaded, sequential code. As mentioned on the section on Concurrency and Parallelism, Scala.Rx allows you to use multiple threads to parallelize the propagation, and allows propagations to be started by multiple threads simultaneously. That means that a strict prohibition of glitches is impossible.

Scala.Rx maintains somewhat looser model: the body of each `Rx` may be evaluated more than once per propagation, and Scala.Rx only promises to make a "best-effort" attempt to reduce the number of redundant updates. Assuming the body of each `Rx` is pure, this means that the redundant updates should only affect the time taken and computation required for the propagation to complete, but not affect the value of each node once the propagation has finished.

In addition, Scala.Rx provides the `Obs`s, which are special terminal-nodes guaranteed to update only once per propagation, intended to produce some side effect. This means that although a propagation may cause the values of the `Rx`s within the dataflow graph to be transiently out of sync, the final side-effects of the propagation will only happen once the entire propagation is complete and the `Obs`s all fire their side effects. 

If multiple propagations are happening in parallel, Scala.Rx guarantees that each `Obs` will fire *at most* once per propagation, and *at least* once overall. Furthermore, each `Obs` will fire at least once after the entire dataflow graph has stabilized and the propagations are complete. This means that if you are relying on `Obs` to, for example, send updates over the network to a remote client, you can be sure that you don't have any unnecessary chatter being transmitted over the network, and when the system is quiescent the remote client will have the updates representing the most up-to-date version of the dataflow graph.

**Next Page: [Related Work](https://github.com/lihaoyi/scala.rx/wiki/Related-Work)**
