# DMA --- EE290C Spring 2021
DMA module used within AES Accelerator and Digital Baseband.

## Authors
Ryan Lund, Anson Tsai

## Table of Contents
[Brief Intro](#brief-intro)

[Installation Instructions](#integration-instructions)

## Brief Intro
The DMA module is a helper module to simplify read and write memory operations on a TileLink interface. 
It can be instantiated in any design and can connect any TileLink interface like so:
```
val dma = LazyModule(new EE290CDMA(p(SystemBusKey).beatBytes, 32, "DMA"))
override val tlNode = dma.id_node
```

### Top-Level Diagram and Implementation
The top-level block diagram is shown below:

![diagram](https://bwrcrepo.eecs.berkeley.edu/EE290C_EE194_tstech28/dma/-/blob/master/diagrams/DMATopLevelDiagram.png?raw=true)

For a brief description, the DMA module consists of two subblocks, the DMAReader and DMAWriter, that handle both read and write requests to the memory.
As each subblock operates independently, the DMA can handle concurrent read and write operations.
More detailed information on the block interface and design can be found in the spec linked below.

### More Documentation/Spec
For more information and details on the implementation of the DMA, documentation can be found in the chip spec
[here](https://docs.google.com/document/d/1J9azqokkR0AsUUAkwU-hotsNtb-0KX5duK7d7f_3MhI/edit?usp=sharing) (you may need to request read access).

## Integration Instructions
This DMA generator requires to be built along with Chipyard, as it has a dependency with the chisel verification library.

### Installing Chipyard
The Chipyard repo and installation instructions can be found at: https://github.com/ucb-bar/chipyard.
Note that the installation instructions below require Chipyard version 1.3.0 or later.

### Installing Chisel Verification Library
Note that we start in the chipyard root directory.
```
~/chipyard> cd tools
~/chipyard/tools> git submodule add https://github.com/TsaiAnson/verif.git
```

### Installing the DMA Generator
Note that we start in the chipyard root directory.
```
~/chipyard> cd generators
~/chipyard/generators> git submodule add (link to this repo)
```

### Modifying your build.sbt
NOTE: There are two parts to this.

First, add the following snippet to the end of `chipyard/build.sbt`:
```
val directoryLayout = Seq(
  scalaSource in Compile := baseDirectory.value / "src",
  javaSource in Compile := baseDirectory.value / "resources",
  resourceDirectory in Compile := baseDirectory.value / "resources",
  scalaSource in Test := baseDirectory.value / "test",
  javaSource in Test := baseDirectory.value / "resources",
  resourceDirectory in Test := baseDirectory.value / "resources",
)

val verifSettings = Seq(
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
  ),
  scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11", "-language:reflectiveCalls"),
  libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.3.1",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.+" % "test"
)

lazy val verifCore = (project in file("./tools/verif/core"))
  .settings(directoryLayout)
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(rocketchip, chipyard, dsptools, `rocket-dsptools`)
  .settings(commonSettings)
  .settings(verifSettings)

lazy val verifTL = (project in file("./tools/verif/tilelink"))
  .settings(directoryLayout)
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(rocketchip, chipyard, dsptools, `rocket-dsptools`, verifCore)
  .settings(commonSettings)
  .settings(verifSettings)

lazy val verifGemmini = (project in file("./tools/verif/cosim"))
  .settings(directoryLayout)
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore, verifTL)
  .settings(commonSettings)
  .settings(verifSettings)
  .settings(libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.14.0")
  .settings(libraryDependencies += "com.google.protobuf" % "protobuf-java-util" % "3.14.0")

lazy val dma = (project in file("generators/dma"))
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore, verifTL, verifGemmini)
  .settings(commonSettings)
  .settings(verifSettings)
```

Next, modify your project definition to depend on this project. Using the `aes` project as an example, it should look like this:
```
lazy val aes = (project in file("generators/aes"))
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore, verifTL, verifGemmini, dma)
  .settings(commonSettings)
  .settings(verifSettings)
```

### Compiling and running the tests
Note that we start in the chipyard root directory.
```
~/chipyard> cd sims/verilator
~/chipyard/sims/verilator> make launch-sbt
sbt:chipyardRoot> project dma
sbt:aes> compile                         // If you just want to compile src code
sbt:aes> test:compile                    // If you just want to compile test code
sbt:aes> testOnly dma.DMAConvertersTest  // Compiles all dependencies and runs specified test
```
