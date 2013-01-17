organization  := "com.rx"

name          := "scalarx"

version       := "0.1"

scalaVersion  := "2.10.0"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.10.0" % "2.0.M5" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.1.0",
  "org.scala-stm" %% "scala-stm" % "0.7"
)
