organization := "woshilaiceshide"

name := "scala-web-repl"

version := "1.0-SNAPSHOT"

compileOrder in Compile := CompileOrder.Mixed

transitiveClassifiers := Seq("sources")

EclipseKeys.withSource := true

scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation","-optimise", "-encoding", "utf8", "-Yno-adapted-args")

javacOptions ++= Seq("-Xlint:deprecation", "-Xlint:unchecked", "-source", "1.7", "-target", "1.7", "-g:vars")

retrieveManaged := false

enablePlugins(JavaAppPackaging)

net.virtualvoid.sbt.graph.Plugin.graphSettings

unmanagedSourceDirectories in Compile <+= baseDirectory( _ / "src" / "java" )

unmanagedSourceDirectories in Compile <+= baseDirectory( _ / "src" / "scala" )

resolvers += Resolver.file("Local", file( Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)

libraryDependencies += "io.spray" %% "spray-json" % "1.3.2"

libraryDependencies += "com.google.guava" % "guava" % "18.0"

libraryDependencies += "woshilaiceshide" %% "s-server" % "1.0"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.11.7"
libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.11.7"
libraryDependencies += "jline" % "jline" % "2.12.1"

mappings in Universal ++= (baseDirectory.value / "conf" * "*" get) map (x => x -> ("conf/" + x.getName))

mainClass in Compile := Some("woshilaiceshide.wrepl.DefaultBootstrap")

