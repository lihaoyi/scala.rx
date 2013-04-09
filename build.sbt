organization  := "com.rx"

name          := "scalarx"

version       := "0.1"

scalaVersion  := "2.10.1"

resolvers += "akka nightlies" at " http://repo.akka.io/snapshots/"

libraryDependencies ++= Seq(
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
    "com.typesafe.akka" %% "akka-actor" % "2.2-SNAPSHOT"
)
