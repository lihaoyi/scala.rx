
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._

scalaJSSettings

Build.sharedSettings

version := "0.1.2-JS"

unmanagedSourceDirectories in Compile <+= baseDirectory(_ / ".." / "shared" / "main")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / ".." / "shared" / "test")

libraryDependencies ++= Seq(
  "com.lihaoyi" % "utest_2.10" % "0.1.0-JS" % "test",
  "org.scala-lang.modules.scalajs" %% "scalajs-dom" % "0.1-SNAPSHOT",
  "org.webjars" % "envjs" % "1.2",
  "com.lihaoyi" % "utest_2.10" % "0.1.0-JS" % "test",
  "com.lihaoyi" % "utest-runner_2.10" % "0.1.0" % "test"
)

(loadedTestFrameworks in Test) := {
  (loadedTestFrameworks in Test).value.updated(
    sbt.TestFramework(classOf[utest.runner.JsFramework].getName),
    new utest.runner.JsFramework(environment = (scalaJSEnvironment in Test).value)
  )
}
