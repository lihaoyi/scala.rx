organization  := "com.rx"

name          := "scalarx"

version       := "0.1"

scalaVersion  := "2.10.0"

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.0")

scalacOptions += "-P:continuations:enable"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.10.0" % "2.0.M5"
)