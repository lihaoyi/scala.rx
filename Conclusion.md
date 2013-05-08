Conclusion
==========
We present Scala.Rx, a change propagation FRP library for Scala. As we have shown, Scala.Rx allows you to take advantage of the change-propagation facet of FRP while imposing almost no constraints on the programmer. It allows the programmer to continue writing in a widely-used programming language, is trivial to set up to start using and provides seamless inter-operability with existing imperative, object-oriented or functional code. 

Scala.Rx allows the programmer to inter-operate with existing code both externally, via `Var`s and `Obs`s, as well as internally, by allowing the use of any Scala language or library construct in building up out dataflow graph from `Rx`s. This is a level of inter-operability which is rarely seen in the FRP world, where often the FRP code is written in either a completely separate language or using an anemic subset of a normal language.

Scala.Rx also provides a useful set of basic combinators, allowing the programmer to construct dataflow graphs with asynchronous, throttled or repeated without needing to write the error-prone asynchronous code manually. Lastly, Scala.Rx provides a powerful mechanism for automatic parallelization of propagations.

Overall, this is a feature set that has not been seen before in the FRP world, and we hope it will help bring FRP one step closer to mainstream software engineering.

Scala.Rx is available on maven central at the coordinates:

```scala
"com.scalarx" % "scalarx_2.10" % "0.1"
```
