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
      new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams(projectName="fa23-mechanical-engineering-main"))) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++                                  // single rocket-core
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 1L) ++ // make mem big enough for multiple binaries

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
      //new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
      new chipyard.config.AbstractConfig
    )
