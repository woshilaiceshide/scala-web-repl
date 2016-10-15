organization := "woshilaiceshide"

name := "scala-web-repl"

version := "2.1"

description := "A scala repl which uses a web terminal as its interactive console, and it can be used as an inspector/cli for running applications, a hot code modification mechanism."

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

publishMavenStyle := true

enablePlugins(BintrayPlugin)

pomIncludeRepository  := {_ => false}

bintrayRepository := "maven"

bintrayOrganization := None

bintrayVcsUrl := Some(s"git@github.com:woshilaiceshide/${name.value}.git")

bintrayReleaseOnPublish in ThisBuild := false

compileOrder in Compile := CompileOrder.Mixed

transitiveClassifiers := Seq("sources")

EclipseKeys.withSource := true

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation","-optimise", "-encoding", "utf8", "-Yno-adapted-args")

javacOptions ++= Seq("-Xlint:deprecation", "-Xlint:unchecked", "-source", "1.8", "-target", "1.8", "-g:vars")

retrieveManaged := false

enablePlugins(JavaAppPackaging)

unmanagedSourceDirectories in Compile <+= baseDirectory( _ / "src" / "java" )

unmanagedSourceDirectories in Compile <+= baseDirectory( _ / "src" / "scala" )

resolvers += Resolver.file("Local", file( Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)

libraryDependencies += "io.spray" %% "spray-json" % "1.3.2"

libraryDependencies += "com.google.guava" % "guava" % "19.0"

libraryDependencies += "woshilaiceshide" %% "s-server" % "2.2"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.11.8"
libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.11.8"

scriptClasspath := "../conf" +: scriptClasspath.value

mappings in Universal ++= (baseDirectory.value / "conf" * "*" get) map (x => x -> ("conf/" + x.getName))

mainClass in Compile := Some("woshilaiceshide.wrepl.DefaultBootstrap")

fork := true

packageOptions in (Compile, packageBin) +=  {
  Package.ManifestAttributes( "Premain-Class" -> "woshilaiceshide.wrepl.Agent" )
}

