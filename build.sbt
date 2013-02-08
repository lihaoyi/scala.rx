organization  := "com.rx"

name          := "scalarx"

version       := "0.1"

scalaVersion  := "2.10.0"

resolvers += "akka nightlies" at " http://repo.akka.io/snapshots/"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.10.0" % "2.0.M5" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.2-20130208-001240",
  "com.typesafe.akka" %% "akka-agent" % "2.2-20130208-001240"
)
