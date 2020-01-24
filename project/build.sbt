// https://www.scala-js.org/news/2018/02/01/announcing-scalajs-1.0.0-M3/#cross-building-for-scalajs-06x-and-1x
val scalaJSVersion = Option(System.getenv("SCALAJS_VERSION")).getOrElse("0.6.32")

// https://github.com/portable-scala/sbt-crossproject
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "0.6.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.3.9")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
