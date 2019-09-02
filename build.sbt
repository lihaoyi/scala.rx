// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{ crossProject, CrossType }

val monocleVersion = "2.0.0-RC1"
val acyclicVersion = "0.2.0"
val crossScalaVersionList = Seq("2.11.12", "2.12.9", "2.13.0")

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
    "-Xfuture" ::
    // TODO:
    // "-Ypartial-unification" ::
    // "-Yno-adapted-args" ::
    // "-Ywarn-infer-any" ::
    // "-Ywarn-value-discard" ::
    // "-Ywarn-nullary-override" ::
    // "-Ywarn-nullary-unit" ::
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
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,

      "com.lihaoyi" %%% "utest" % "0.6.9" % "test",
      "com.lihaoyi" %% "acyclic" % acyclicVersion % "provided"
    ),
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % acyclicVersion),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    autoCompilerPlugins := true,

    // Sonatype
    publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
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

lazy val bench =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(sharedSettings)
    .dependsOn(scalarx)
    .settings(
      resolvers += ("jitpack" at "https://jitpack.io"),
      libraryDependencies ++=
        "com.github.fdietze.bench" %%% "bench" % "8442b94" ::
        Nil,

      scalacOptions ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, major)) if major == 11 => (
            "-Xdisable-assertions" ::
            /* "-optimise" :: */
            /* "-Yclosure-elim" :: */
            /* "-Yinline" :: */
            Nil
          )
          case Some((2, major)) if major >= 12 => (
            "-Xdisable-assertions" ::
            "-opt:l:method" ::
            "-opt:l:inline" ::
            "-opt-inline-from:**" ::
            Nil
          )
          case _ => Seq.empty
        }
      },
    )
    .jsSettings(
      scalaJSStage in Compile := FullOptStage,
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    )
