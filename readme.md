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

Apart from basic change-propagation, Scala.Rx provides a host of other functionality, such as a set of combinators for easily constructing the dataflow graph, automatic parallelization of updates, and seamless interop with existing Scala code. This means it can be easily embedded in an existing Scala application.

Contents
========

- [Getting Started](#getting-started)
- [ScalaJS](#scalajs)
- [Using Scala.Rx](#using-scalarx)
  - [Basic Usage](#basic-operations)
  - [Combinators](#combinators)
  - [Graph Inspection](#graph-inspection)
  - [Logging and Debugging](#logging-and-debugging)
- [Execution Model](#execution-model)
  - [Concurrency](#concurrency)
  - [Garbage Collection](#garbage-collection)
  - [Internals](#internals)
- [Related Work](#related-work)
- [Mailing List]()
- [Scaladoc]() (and the [ScalaJS version]())

This document goes through the usage of Scala.Rx, the implementation that makes everything work, the design considerations and limitations inherent in the system and ends with a brief overview of related work.

Getting Started
===============

Scala.Rx is available on [Maven Central](http://search.maven.org/#artifactdetails%7Ccom.scalarx%7Cscalarx_2.10%7C0.1%7Cjar). In order to get started, simply add the following to your `build.sbt`:

```scala
libraryDependencies += "com.scalarx" % "scalarx_2.10" % "0.2.0"
```

After that, opening up the `sbt console` and pasting the above example into the console should work! You can proceed through the examples in the [Basic Usage](#basic-usage) page to get a feel for what Scala.Rx can do.

ScalaJS
=======

In addition to running on the JVM, Scala.Rx also compiles to [Scala-Js]()! This artifact can be found on Maven Central at:

```scala
libraryDependencies += "com.scalarx" % "scalarx_2.10" % "0.2.0-JS"
```

There are some minor differences between running Scala.Rx on the JVM and in Javascript particularly around [asynchronous operations](), the [concurrency model]() and [garbage collection](). In general, though, all the examples given in the documentation below will work perfectly when cross-compiled to javascript and run in the browser!

Using Scala.Rx
==============

Basic Usage
-----------

Combinators
-----------

Graph Inspection
----------------

Logging and Debugging
---------------------

Execution Model
===============

Concurrency
-----------

Garbage Collection
------------------

Internals
---------

Related Work
============

Feel free to fork the repository and try it out, or browse the [online ScalaDoc](http://lihaoyi.github.io/scala.rx/#package). Contributions are welcome!

Credits
=======

Copyright (c) 2013, Li Haoyi (haoyi.sg at gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
