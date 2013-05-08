How it Works
============
You have by now seen how Scala.Rx works, from the point of view of a user of the API. This section will elaborate on the underlying implementation.  

Dependency Tracking
-------------------
Scala.Rx tracks the dependency graph between different `Var`s and `Rx`s without any explicit annotation by the programmer. This means that in (almost) all cases, you can just write your code as if it wasn't being tracked, and Scala.Rx would build up the dependency graph automatically.

Every time the body of an `Rx{...}` is evaluated (or re-evaluated), it is put into a `DynamicVariable`. Any calls to the `.apply()` methods of other `Rx`s then inspect this `DynamicVariable` to determine who (if any) is the `Rx` being evaluated. This is linked up with the `Rx` whose `.apply()` is being called, creating a dependency between them. Thus a dependency graph is implicitly created without any action on the part of the programmer.

The dependency-tracking strategy of Scala.Rx is based of a subset of the ideas in [Deprecating the Observer Pattern](http://infoscience.epfl.ch/record/176887/files/DeprecatingObservers2012.pdf), in particular their definition of "Opaque Signals". The implementation follows it reasonably closely.

Propagation
-----------

###Weak Forward References
Once we have evaluated our `Var`s and `Rx`s once and have a dependency graph, how do we keep track of our children (the `Rx`s who depend on us) and tell them to update? Simply keeping a `List[]` of all children will cause memory leaks, as the `List[]` will prevent any child from being garbage collected even if all other references to the child have been lost and the child is otherwise unaccessable.

Instead, Scala.Rx using a list of [WeakReferences](http://en.wikipedia.org/wiki/Weak_reference). These allow the `Rx` to keep track of its children while still letting them get garbage collected when all other references to them are lost. When a child becomes unreachable and gets garbage collected, the WeakReference becomes `null`, and these null references get cleared from the list every time it is updated.

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
By default, everything happens on a single-threaded execution context and there is no parallelism. By using a custom [ExecutionContext](http://www.scala-lang.org/archives/downloads/distrib/files/nightly/docs/library/index.html#scala.concurrent.ExecutionContext), it is possible to have the updates in each propagation run happen in parallel. For more information on ExecutionContexts, see the [Akka Documentation](http://doc.akka.io/docs/akka/2.1.2/scala/futures.html#futures-scala). The [unit tests](../tree/master/src/test/scala/rx/ParallelTests.scala) also contain an example of a dependency graph whose evaluation is spread over multiple threads in this way.

Even without using an explicitly parallelising ExecutionContext, parallelism could creep into your code in subtle ways: a delayed `Rx`, for example may happen to fire and continue its propagation just as you update a `Var` somewhere on the same dependency graph, resulting in two propagations proceeding in parallel.

In general, the rules for parallel execution of an individual node in the dependency graph is as follows:

- A `Rx` can up updated by an arbitrarily high number of parallel threads: *make sure your code can handle this!*. I will refer to each of these as a *parallel update*.
- A parallel update **U** gets committed (i.e. its result becomes the value of the `Rx` after completion) *if and only if* the time at which **U**'s computation began (its *start time*) is greater than the start time of the last-committed parallel update.
- The state of the dataflow graph may be temporarily inconsistent as a propagation is happening. This is even more true when multiple propagations are happening simultaneously. However, after all propagations complete and the system stabilizes, it is guaranteed that the value of every `Rx` will be correct, with regard to the most up-to-date values of its dependencies.
- When a single propagation happens, Scala.Rx guarantees that although the dataflow graph may be transiently inconsistent, the `Obs` which produce side effects will only fire once everything has stabilized.
- In the presence of multiple propagations happening in parallel, Scala.Rx guarantees that each `Obs` will fire *at most* once per propagation that occurs. Furthermore, by the time the propagations have completed and the system has stabilized, each `Obs` will have fired *at least once* after the `Rx` it is observing has reached its final value.

This policy is implemented using the __java.util.concurrent.Atomic*__ classes. The final compare-start-times-and-replace-if-timestamp-is-greater action is implemented as a STM-style retry-loop on an __AtomicReference__. This approach is [lock free](http://en.wikipedia.org/wiki/Non-blocking_algorithm#Lock-freedom), and since the time required to compute the result is probably far greater than the time spent trying to commit it, the number of retries should in practice be minimal.

###Weak Forward References
The weak-forward-references to an `Rx` from its dependencies is unusual in that unlike the rest of the state regarding the `Rx`, it is not kept within the `Rx` itself! Rather, it is kept within its parents. Hence updates to these weak references cannot conveniently be seralized by encapsulating the state within that `Rx`'s state.

Instead, Scala.Rx does two things:

- Make the list of WeakReferences append-only
- Handle appends to the list via an atomic [CAS](http://en.wikipedia.org/wiki/Compare-and-swap), with a re-try if there is a conflict.
- Maintains a list of Parents in each Child, in addition to having a list of Children in each Parent. This list of parents will then be kept up to date, and updates to it will be serialized when the `Rx`'s Agent updates.

As a result, although the _forward_ references from parent to child may not always be kept up to date, they will always form a super-set of the "correct" relationships. These "correct" relationships will be kept up to date in the _backward_ references from child to parent, and will ensure that things behave correctly even if the set of forward references is larger than it needs to be. The atomic-CAS-with-re-try provides an elegant, lock-free mechanism by which forward references can be appended to this list.

**Next Section: [Design Considerations](https://github.com/lihaoyi/scala.rx/wiki/Design-Considerations)**
