package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{InCluster}
import testchipip.boot.{BootAddrRegKey, BootAddrRegParams}

import testchipip.serdes.{CanHavePeripheryTLSerial, SerialTLKey}
import freechips.rocketchip.devices.tilelink.{CLINTParams, CLINTKey}



import freechips.rocketchip.subsystem.{ExtBus, ExtMem, MemoryPortParams, MasterPortParams, SlavePortParams, MemoryBusKey}

// --------------
// OFO (EECS 151 ASIC) Core Configs
// --------------

class OFORocketConfig
    extends Config(
      new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams())) ++
  new WithOFOSoCModifications ++ // enable dual memory regions, start the rocket at 0xa0000000
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++                                  // single rocket-core

  // TODO: make tinycore work, currently at least printing doesn't work persumably bc of the cache being a spad that TSI can't access (and thus can't print from)

  // new chipyard.harness.WithDontTouchChipTopPorts(false) ++        // TODO FIX: Don't dontTouch the ports
  // new testchipip.soc.WithNoScratchpads ++                         // All memory is the Rocket TCMs
  // new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++ // use incoherent bus topology
  // new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  // new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
        new chipyard.config.AbstractConfig
    )
class WithOFOSoCModifications extends Config(
   new Config((site, here, up) => {
    // disable tsi on this soc
    //case SerialTLKey => None
    // move CLINT to know addr
    //case CLINTKey => Some(CLINTParams(baseAddress = BigInt("a0000000", 16)))
    // have bootrom jump to proper dram loc
    case BootAddrRegKey => up(BootAddrRegKey).map(_.copy(defaultBootAddress = BigInt("90000000", 16)))
  }) ++
  // setup memory to be at different location
  new Config((site, here, up) => {
    case ExtMem => Some(MemoryPortParams(MasterPortParams(
      base = BigInt("80000000", 16),
      size = BigInt("40000000", 16),
      beatBytes = site(MemoryBusKey).beatBytes,
      idBits = 7), // has to be 7 to match the app soc
      1 // 1 mem. channels
    ))
  }) 
/*
  // setup slave port (slave to AppSoC)
  new Config((site, here, up) => {
    case ExtIn => Some(SlavePortParams(
      beatBytes = 8, // 64b of data per xfer
      idBits = 4, // 4b of source
      sourceBits = 1 // ?? changes nothing
    ))
  }) ++
  // setup master port (master to AppSoC)
  new Config((site, here, up) => {
    case ExtBus2 => Some(MasterPortParams(
      base = 0x8000_0000,
      size = 0x1000_0000,
      beatBytes = site(MemoryBusKey).beatBytes, // 64b of data per xfer
      idBits = 4, // 4b of source
      executable = true // left true otherwise it will add extra bundle fields
    ))
  })
*/
)
