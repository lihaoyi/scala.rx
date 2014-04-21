import sbt._
import Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
object Build extends sbt.Build{
  lazy val root = project.in(file("."))
                         .aggregate(js, jvm)
                         .settings(crossScalaVersions := Seq("2.10.4", "2.11.0"))

  lazy val js = project.in(file("js"))
                       .settings(sharedSettings ++ scalaJSSettings:_*)
                       .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules.scalajs" %% "scalajs-dom" % "0.3" % "provided",
      "org.webjars" % "envjs" % "1.2" % "test",
      "com.lihaoyi" %% "utest" % "0.1.3-JS" % "test",
      "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided"
    ),
    version := version.value + "-JS",
    (loadedTestFrameworks in Test) := {
      (loadedTestFrameworks in Test).value.updated(
        sbt.TestFramework(classOf[utest.runner.JsFramework].getName),
        new utest.runner.JsFramework(environment = (scalaJSEnvironment in Test).value)
      )
    }
  )

  lazy val jvm = project.in(file("jvm"))
                        .settings(sharedSettings:_*)
                        .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.3.2" % "provided",
      "com.lihaoyi" %% "utest" % "0.1.3" % "test",
      "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided"
    ),
    testFrameworks += new TestFramework("utest.runner.JvmFramework")
  )

  val sharedSettings = Seq(
    organization := "com.scalarx",
    name := "scalarx",
    scalaVersion := "2.10.4",
    version := "0.2.4",
    unmanagedSourceDirectories in Compile <+= baseDirectory(_ / ".." / "shared" / "main" / "scala"),
    unmanagedSourceDirectories in Test <+= baseDirectory(_ / ".." / "shared" / "test" / "scala"),
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.2"),
    autoCompilerPlugins := true,
    // Sonatype
    publishArtifact in Test := false,
    publishTo <<= version { (v: String) =>
      Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    },

    pomExtra := (
      <url>https://github.com/lihaoyi/scalatags</url>
        <licenses>
          <license>
            <name>MIT license</name>
            <url>http://www.opensource.org/licenses/mit-license.php</url>
          </license>
        </licenses>
        <scm>
          <url>git://github.com/lihaoyi/scalatags.git</url>
          <connection>scm:git://github.com/lihaoyi/scalatags.git</connection>
        </scm>
        <developers>
          <developer>
            <id>lihaoyi</id>
            <name>Li Haoyi</name>
            <url>https://github.com/lihaoyi</url>
          </developer>
        </developers>
      )
  )
}
