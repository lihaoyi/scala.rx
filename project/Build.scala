import sbt._
import Keys._
import scala.scalajs.sbtplugin.env.nodejs.NodeJSEnv
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.env.phantomjs.PhantomJSEnv
object Build extends sbt.Build{
  val cross = new utest.jsrunner.JsCrossBuild(
    organization := "com.scalarx",
    name := "scalarx",
    scalaVersion := "2.10.4",
    version := "0.2.6",

    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.2"),
    autoCompilerPlugins := true,
    // Sonatype

    publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),

    pomExtra :=
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
  lazy val root = cross.root

  lazy val js = cross.js.settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6" % "provided",
      "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided"
    ),
    test in Test := (test in (Test, fastOptStage)).value
  ).configure(sourceMapsToGithub)

  lazy val jvm = cross.jvm.settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.3.2" % "provided",
      "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided"
    )
  )

  def sourceMapsToGithub: Project => Project =
    p => p.settings(
      scalacOptions ++= (if (isSnapshot.value) Seq.empty else Seq({
        val a = p.base.toURI.toString.replaceFirst("[^/]+/?$", "")
        val g = "https://raw.githubusercontent.com/lihaoyi/scala.rx"
        s"-P:scalajs:mapSourceURI:$a->$g/${version.value}/"
      }))
    )
}
