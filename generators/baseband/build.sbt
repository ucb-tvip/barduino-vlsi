// name := "baseband"
// organization := "edu.berkeley.cs"
// version := "0.0.1"

// scalaVersion := "2.13.10"

// scalacOptions += "-language:higherKinds"
// addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full)

// scalacOptions += "-Ydelambdafy:inline"
// scalacOptions ++= Seq(
//   "-deprecation",
//   "-encoding", "UTF-8",
//   "-feature",
//   "-unchecked",
//   "-Xfatal-warnings",
//   "-language:reflectiveCalls",
//   "-Ymacro-annotations"
// )

// val chiselVersion = "5.1.0"
// addCompilerPlugin("org.chipsalliance" %% "chisel-plugin" % chiselVersion cross CrossVersion.full)
// libraryDependencies ++= Seq(
//   "org.chipsalliance" %% "chisel" % chiselVersion,
//   "org.scalatest" %% "scalatest" % "3.2.15" % "test",
//   "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
//   "org.scalanlp" %% "breeze-viz" % "1.1"
// )

// val directoryLayout = Seq(
//   scalaSource in Compile := baseDirectory.value / "src",
//   resourceDirectory in Compile := baseDirectory.value / "resources",
//   scalaSource in Test := baseDirectory.value / "test",
//   resourceDirectory in Test := baseDirectory.value / "resources",
// )

// val verifSettings = Seq(
//   scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11", "-language:reflectiveCalls"),
//   libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.3.1",
//   libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.+" % "test",
//   libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.4.+",
//   libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.2.3"
// )

// lazy val verif = (project in file("./verif/core"))
//   .settings(directoryLayout)
//   .settings(verifSettings)
