// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{ crossProject, CrossType }

val monocleVersion = "1.5.1-cats"
val acyclicVersion = "0.1.9"
val crossScalaVersionList = Seq("2.11.12", "2.12.8")

val sharedSettings = Seq(
  crossScalaVersions := crossScalaVersionList,
  scalaVersion := crossScalaVersionList.last,
  version := "0.4.0",
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Xcheckinit" ::
    "-Xfuture" ::
    "-Ypartial-unification" ::
    "-Yno-adapted-args" ::
    "-Ywarn-infer-any" ::
    "-Ywarn-value-discard" ::
    "-Ywarn-nullary-override" ::
    "-Ywarn-nullary-unit" ::
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 12 =>
        "-Xlint:-unused" :: // too many false positives for unused because of acyclic, macros, local vals in tests
        Nil
      case _ => Nil
    }),
)

lazy val scalarx = crossProject(JSPlatform, JVMPlatform)
  .settings(sharedSettings)
  .settings(
    organization := "com.lihaoyi",
    name := "scalarx",

    libraryDependencies ++= Seq(
      "com.github.julien-truffaut" %%% "monocle-core" % monocleVersion,
      "com.github.julien-truffaut" %%% "monocle-macro" % monocleVersion % "test",

      "com.lihaoyi" %%% "sourcecode" % "0.1.7",
      "com.lihaoyi" %%% "utest" % "0.6.7" % "test",
      "com.lihaoyi" %% "acyclic" % acyclicVersion % "provided"
    ),
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % acyclicVersion),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    autoCompilerPlugins := true,

  // Sonatype
  publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
  pomExtra :=
    <url>https://github.com/lihaoyi/scala.rx</url>
      <licenses>
        <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <developers>
        <developer>
          <id>lihaoyi</id>
          <name>Li Haoyi</name>
          <url>https://github.com/lihaoyi</url>
        </developer>
      </developers>
).jsSettings(
  scalaJSStage in Test := FullOptStage,
  scalacOptions += {
    val local = baseDirectory.value.toURI
    val remote = s"https://raw.githubusercontent.com/lihaoyi/scala.rx/${git.gitHeadCommit.value.get}/"
    s"-P:scalajs:mapSourceURI:$local->$remote"
  }
)

lazy val js = scalarx.js
lazy val jvm = scalarx.jvm
