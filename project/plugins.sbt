addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.4.1")

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers +=  Resolver.url("scala-js-releases",
  url("http://dl.bintray.com/content/scala-js/scala-js-releases"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("com.lihaoyi" % "utest-js-plugin" % "0.1.2")

//generates IDEA files
addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

//dependency graph generation
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")
