addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.3-SNAPSHOT")

addSbtPlugin("com.lihaoyi" % "utest-js-plugin" % "0.1.0")

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.lihaoyi" % "utest-js-plugin" % "0.1.0")

libraryDependencies ++= Seq(
  "org.scala-sbt" % "test-interface" % "1.0",
  "com.lihaoyi" % "utest-runner_2.10" % "0.1.0"
)
