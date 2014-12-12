//// REQUIRED SETUP                                                                                                                    
                                                                                                                                       
// Setup bintray resolver - needed for many sbt plugins                                                                                
resolvers += Resolver.url(                                                                                                             
  "bintray-sbt-plugin-releases",                                                                                                       
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(                                                                       
  Resolver.ivyStylePatterns)                                                                                                           
                                                                                                                                       
libraryDependencies ++= Seq(                                                                                                           
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.0.0.201306101825-r"                                                                     
)                                                                                                                                      
                                                                                                                                       
resolvers += Classpaths.sbtPluginReleases                                                                                              
                                                                                                                                       
resolvers += Classpaths.typesafeReleases                                                                                               
                                                                                                                                       
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")                                                                                    
                                                                                                                                       
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "0.99.5.1")                                                                          
                                                                                                                                       
addSbtPlugin("com.sksamuel.scoverage" %% "sbt-coveralls" % "0.0.5")                                                                    
                                                                                                                                       
addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.3")                                                                                 
                                                                                                                                       
// Wrapper plugin for scalajs                                                                                                          
addSbtPlugin("com.github.inthenow" % "sbt-scalajs" % "0.56.6")                                                                         
                                                                                                                                       
addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")