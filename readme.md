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

- [Basic Usage](https://github.com/lihaoyi/scala.rx/wiki/Basic-Usage): how to use Scala.Rx
- [Combinators](https://github.com/lihaoyi/scala.rx/wiki/Combinators): useful ways of transforming `Rx`s to modify their values, combine them, turn them asynchronous delay them, etc.
- [How it Works](https://github.com/lihaoyi/scala.rx/wiki/How-it-Works): The nitty-gritty of how Scala.Rx is implemented under the hood
- [Design Considerations](https://github.com/lihaoyi/scala.rx/wiki/Design-Considerations): If you want to find out more about the thought process and considerations that went into Scala.Rx

Credits
=======

Copyright (c) 2013, Li Haoyi (haoyi.sg at gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.