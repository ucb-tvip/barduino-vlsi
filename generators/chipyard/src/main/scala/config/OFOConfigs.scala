package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{InCluster}
import testchipip.boot.{BootAddrRegKey, BootAddrRegParams}
import freechips.rocketchip.subsystem.{MBUS, SBUS}

import testchipip.serdes.{CanHavePeripheryTLSerial, SerialTLKey}
import freechips.rocketchip.devices.tilelink.{CLINTParams, CLINTKey}

import freechips.rocketchip.subsystem.{ExtBus, ExtMem, MemoryPortParams, MasterPortParams, SlavePortParams, MemoryBusKey}

// --------------
// OFO (EECS 151 ASIC) Core Configs
// --------------

class OFORawConfig extends Config(
  new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams(projectName="kevin-kore"))) ++
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 1L) ++ // make mem big enough for multiple binaries
  new chipyard.config.AbstractConfig
)

class TemplateOFORocketConfig extends Config(
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++    // Add a small "control" core
  new freechips.rocketchip.rocket.WithL1ICacheSets(64) ++ // 64 sets, 1 way, 4K cache
  new freechips.rocketchip.rocket.WithL1ICacheWays(1) ++
  new freechips.rocketchip.rocket.WithL1DCacheSets(64) ++ // 64 sets, 1 way, 4K cache
  new freechips.rocketchip.rocket.WithL1DCacheWays(1) ++

  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 1L) ++ // make mem big enough for multiple binaries
  // TODO: make tinycore work, currently at least printing doesn't work persumably bc of the cache being a spad that TSI can't access (and thus can't print from)
  new chipyard.config.AbstractConfig
)

class TemplateOFOTConfig extends Config( // toplevel
  //TODO Remove Simulation collateral
  new chipyard.sky130.WithVerilogDummySky130EFCaravelPOR ++

  // heavily referencing STACConfig and ChipLikeConfig
  // don't have enough area for these
  new chipyard.config.WithBroadcastManager ++                      // Replace L2 with a broadcast hub for coherence
  new testchipip.soc.WithNoScratchpads ++                      // No scratchpads
  
  //==================================
  // Set up I/O
  //==================================
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(              // 1 serial tilelink port
    manager = Some(testchipip.serdes.SerialTLManagerParams(                             // port acts as a manager of offchip memory
      memParams = Seq(testchipip.serdes.ManagerRAMParams(                               // 4 GB of off-chip memory
        address = BigInt("80000000", 16),
        size    = BigInt("100000000", 16)
      )),
      isMemoryDevice = true
    )),
    client = Some(testchipip.serdes.SerialTLClientParams()),                            // Allow an external manager to probe this chip
    phyParams = testchipip.serdes.ExternalSyncSerialPhyParams(phitWidth=1, flitWidth=16)   // 1-bit bidir interface, sync'd to an external clock
  ))) ++

  new freechips.rocketchip.subsystem.WithNoMemPort ++                                   // Remove axi4 mem port
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

  // single clock over IO
  new chipyard.clocking.WithPureIOClockSky130(freqMHz = 5) ++

  //==================================
  // Set up buses
  //==================================
  new testchipip.soc.WithOffchipBusClient(MBUS) ++                                      // offchip bus connects to MBUS, since the serial-tl needs to provide backing memory
  new testchipip.soc.WithOffchipBus ++                                                  // attach a offchip bus, since the serial-tl will master some external tilelink memory

  // set up io ring
  new chipyard.sky130.WithSky130EFIOCells(sim = false) ++
  new chipyard.sky130.WithSky130EFIOTotalCells(46) ++
  new chipyard.sky130.WithSky130ChipTop ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology
)

class ProvenOFOTConfig extends Config(
  // SoC harness
  new TemplateOFOTConfig ++
  // actually include the ofo core
  new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams(projectName="kevin-kore"))) ++
  // actually include the tiny rocket
  new TemplateOFORocketConfig
)

// --------------
// More experimental OFO Configs
// --------------

class DualOFOTConfig extends Config(
  // SoC harness
  new TemplateOFOTConfig ++
  // actually include the ofo core
  new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams(projectName="kevin-kore"), 
    ofo.OneFiftyOneCoreParams(projectName="kevin-kore"))) ++
  // actually include the tiny rocket
  new TemplateOFORocketConfig
)

class MultiOFOTConfig extends Config(
  // SoC harness
  new TemplateOFOTConfig ++
  // actually include the ofo core
  new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams(projectName="kevin-kore"), 
    ofo.OneFiftyOneCoreParams(projectName="fa23-mechanical-engineering-main"))) ++
  // actually include the tiny rocket
  new TemplateOFORocketConfig
)
