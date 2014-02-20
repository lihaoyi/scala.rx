
import sbt._
import sbt.Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._

scalaJSSettings

Build.sharedSettings

version := "0.2.2-JS"

unmanagedSourceDirectories in Compile <+= baseDirectory(_ / ".." / "shared" / "main")

unmanagedSourceDirectories in Test <+= baseDirectory(_ / ".." / "shared" / "test")

libraryDependencies ++= Seq(
  "org.scala-lang.modules.scalajs" %% "scalajs-dom" % "0.1",
  "org.webjars" % "envjs" % "1.2",
  "com.lihaoyi.utest" % "utest_2.10" % "0.1.1-JS" % "test",
  "com.lihaoyi.acyclic" %% "acyclic" % "0.1.0" % "provided"
)

(loadedTestFrameworks in Test) := {
  (loadedTestFrameworks in Test).value.updated(
    sbt.TestFramework(classOf[utest.runner.JsFramework].getName),
    new utest.runner.JsFramework(environment = (scalaJSEnvironment in Test).value)
  )
}

addCompilerPlugin("com.lihaoyi.acyclic" %% "acyclic" % "0.1.0")

autoCompilerPlugins := true
