addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.3")

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.lihaoyi.utest" % "utest-js-plugin" % "0.1.1")

libraryDependencies ++= Seq(
  "com.lihaoyi.utest" % "utest-runner_2.10" % "0.1.1"
)
