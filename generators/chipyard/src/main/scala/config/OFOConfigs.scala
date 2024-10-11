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

class OFORocketConfig
    extends Config(
      new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams())) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++                                  // single rocket-core
  // TODO: make tinycore work, currently at least printing doesn't work persumably bc of the cache being a spad that TSI can't access (and thus can't print from)

  // new chipyard.harness.WithDontTouchChipTopPorts(false) ++        // TODO FIX: Don't dontTouch the ports
  // new testchipip.soc.WithNoScratchpads ++                         // All memory is the Rocket TCMs
  // new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++ // use incoherent bus topology
  // new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  // new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
        new chipyard.config.AbstractConfig
    )
class OFORawConfig
    extends Config(
      new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams(projectName="kevin-kore"))) ++

      new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 1L) ++ // make mem big enough for multiple binaries
      new chipyard.config.AbstractConfig
    )

class OFOTConfig extends Config( // toplevel
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
 new chipyard.sky130.WithSky130EFIOCells(sim = true) ++
 new chipyard.sky130.WithSky130EFIOTotalCells(46) ++
 new chipyard.sky130.WithSky130ChipTop ++
 new freechips.rocketchip.subsystem.WithCoherentBusTopology ++

 // actually include the ofo core and tiny rocket
 new OFORocketConfig  
  )
