name := "ee290cdma"
organization := "edu.berkeley.cs"
version := "0.0.1"

scalaVersion := "2.13.10"

scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11", "-language:reflectiveCalls")
libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % "3.6.0" ,
                            "edu.berkeley.cs" %% "rocketchip" % "1.2.+",
                            "edu.berkeley.cs" %% "chiseltest" % "0.6.0",
                            "edu.berkeley.cs" %% "testchipip" % "1.0-031419-SNAPSHOT",
                            "org.scalatest" %% "scalatest" % "3.2.+" % "test")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal)
