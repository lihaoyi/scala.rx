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

This document goes through the usage of Scala.Rx, the implementation that makes everything work, the design considerations and limitations inherent in the system and ends with a brief overview of related work.

Scala.Rx is available on [Maven Central](http://search.maven.org/#artifactdetails%7Ccom.scalarx%7Cscalarx_2.10%7C0.1%7Cjar). In order to get started, simply add the following to your `build.sbt`:

```scala
libraryDependencies += "com.scalarx" % "scalarx_2.10" % "0.1"
```

After that, opening up the `sbt console` and pasting the above example into the console should work! You can proceed through the examples in the [Basic Usage](https://github.com/lihaoyi/scala.rx/wiki/Basic-Usage) page to get a feel for what Scala.Rx can do.

- [Basic Usage](https://github.com/lihaoyi/scala.rx/wiki/Basic-Usage): how to use Scala.Rx to construct *dataflow graphs*, whose values will automatically be kept in sync.
- [Combinators](https://github.com/lihaoyi/scala.rx/wiki/Combinators): useful ways of transforming `Rx`s to modify their values, combine them, turn them asynchronous, delay them, etc.
- [How it Works](https://github.com/lihaoyi/scala.rx/wiki/How-it-Works): The nitty-gritty of how Scala.Rx is implemented under the hood
- [Design Considerations](https://github.com/lihaoyi/scala.rx/wiki/Design-Considerations): If you want to find out more about the thought process and considerations that went into Scala.Rx
- [Related Work](https://github.com/lihaoyi/scala.rx/wiki/Related-Work): Read about other work in the field of Functional Reactive Programming that inspired Scala.Rx

Feel free to fork the repository and try it out, or browse the [online ScalaDoc](http://lihaoyi.github.io/scala.rx/#package). Contributions are welcome!

Credits
=======

Copyright (c) 2013, Li Haoyi (haoyi.sg at gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
